package io.github.lalala1314521.codereviewagent.review.diff;

import io.github.lalala1314521.codereviewagent.model.DiffFile;
import io.github.lalala1314521.codereviewagent.model.DiffHunk;
import io.github.lalala1314521.codereviewagent.model.HunkLine;
import io.github.lalala1314521.codereviewagent.model.LineType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * unified diff 解析器：文本 → 结构化 DiffFile 列表。
 *
 * <p>兼容两种输入：
 * <ul>
 *   <li><b>完整 diff</b>（生产）：含 {@code diff --git} / {@code ---} / {@code +++} 文件头</li>
 *   <li><b>裸 hunk</b>（评测用例）：直接以 {@code @@} 开头，归入匿名文件（路径由调用方补充）</li>
 * </ul>
 *
 * <p>行号推进：DEL 只推旧行号、ADD 只推新行号、CONTEXT 双侧都推。
 * 删除行 newLineNumber=-1，新增行 oldLineNumber=-1。
 */
@Component
public class DiffParser {

    private static final Pattern HUNK_HEADER =
            Pattern.compile("@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    /** 后缀 → 语言映射（规则按语言匹配用） */
    private static final Map<String, String> EXT_TO_LANGUAGE = Map.ofEntries(
            Map.entry("java", "java"),
            Map.entry("kt", "kotlin"),
            Map.entry("go", "go"),
            Map.entry("ts", "ts"),
            Map.entry("tsx", "ts"),
            Map.entry("js", "js"),
            Map.entry("jsx", "js"),
            Map.entry("py", "python"),
            Map.entry("sql", "sql"),
            Map.entry("yml", "yaml"),
            Map.entry("yaml", "yaml"),
            Map.entry("xml", "xml"),
            Map.entry("vue", "vue"),
            Map.entry("css", "css"),
            Map.entry("md", "md")
    );

    /**
     * 解析 diff 文本（生产入口）：完整 diff，含文件头。
     */
    public List<DiffFile> parse(String diffText) {
        return parse(diffText, null);
    }

    /**
     * 解析 diff 文本。
     *
     * @param diffText    unified diff（完整或裸 hunk）；null/空白返回空列表
     * @param defaultPath 裸 hunk（无文件头）时的兜底文件路径；生产传 null（必有文件头）
     */
    public List<DiffFile> parse(String diffText, String defaultPath) {
        List<DiffFile> files = new ArrayList<>();
        if (diffText == null || diffText.isBlank()) {
            return files;
        }

        String[] lines = diffText.split("\n", -1);
        FileBuilder currentFile = null;
        List<HunkLine> currentHunkLines = null;
        int oldStart = 0, newStart = 0, hunkOld = 0, hunkNew = 0;
        int oldLineNo = 0, newLineNo = 0;

        for (String raw : lines) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;

            if (line.startsWith("diff --git")) {
                // 新文件开始：结算上一个
                currentFile = flushHunk(files, currentFile, currentHunkLines, oldStart, newStart, hunkOld, hunkNew);
                currentHunkLines = null;
                currentFile = new FileBuilder();
                continue;
            }
            if (currentFile == null) {
                // 裸 hunk（无 diff --git 头）：建匿名文件
                currentFile = new FileBuilder();
            }
            if (line.startsWith("--- ")) {
                currentFile.oldPath = stripPrefix(line.substring(4).trim());
                continue;
            }
            if (line.startsWith("+++ ")) {
                currentFile.newPath = stripPrefix(line.substring(4).trim());
                continue;
            }
            if (line.startsWith("new file mode")) {
                currentFile.isAdded = true;
                continue;
            }
            if (line.startsWith("deleted file mode")) {
                currentFile.isDeleted = true;
                continue;
            }
            if (line.startsWith("rename from") || line.startsWith("rename to")) {
                currentFile.isRenamed = true;
                continue;
            }

            Matcher hm = HUNK_HEADER.matcher(line);
            if (hm.find()) {
                // 新 hunk：结算上一个 hunk
                flushHunkInto(currentFile, currentHunkLines, oldStart, newStart, hunkOld, hunkNew);
                oldStart = Integer.parseInt(hm.group(1));
                newStart = Integer.parseInt(hm.group(2));
                oldLineNo = oldStart;
                newLineNo = newStart;
                hunkOld = oldStart;
                hunkNew = newStart;
                currentHunkLines = new ArrayList<>();
                continue;
            }
            if (currentHunkLines == null) {
                // 文件头与 hunk 之间的元信息行（index / Binary 等），跳过
                continue;
            }

            if (line.startsWith("+")) {
                currentHunkLines.add(new HunkLine(LineType.ADD, -1, newLineNo++, line.substring(1)));
            } else if (line.startsWith("-")) {
                currentHunkLines.add(new HunkLine(LineType.DEL, oldLineNo++, -1, line.substring(1)));
            } else if (line.startsWith("\\")) {
                // "\ No newline at end of file" 标记行，忽略
            } else {
                // 上下文行（可能以空格开头，或空行）
                String content = line.startsWith(" ") ? line.substring(1) : line;
                currentHunkLines.add(new HunkLine(LineType.CONTEXT, oldLineNo++, newLineNo++, content));
            }
        }

        // 结算尾部
        flushHunkInto(currentFile, currentHunkLines, oldStart, newStart, hunkOld, hunkNew);
        if (currentFile != null && !currentFile.hunks.isEmpty()) {
            files.add(currentFile.build(defaultPath));
        }
        return files;
    }

    /**
     * 由文件路径推断语言。
     */
    public static String detectLanguage(String path) {
        if (path == null) {
            return "unknown";
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return "unknown";
        }
        return EXT_TO_LANGUAGE.getOrDefault(path.substring(dot + 1).toLowerCase(), "unknown");
    }

    /** 去掉 a/ b/ 前缀；/dev/null（新增/删除文件）原样保留 */
    private String stripPrefix(String path) {
        if (path.startsWith("a/") || path.startsWith("b/")) {
            return path.substring(2);
        }
        return path;
    }

    private FileBuilder flushHunk(List<DiffFile> files, FileBuilder file, List<HunkLine> hunkLines,
                                  int oldStart, int newStart, int hunkOld, int hunkNew) {
        flushHunkInto(file, hunkLines, oldStart, newStart, hunkOld, hunkNew);
        if (file != null && !file.hunks.isEmpty()) {
            files.add(file.build(null));
        }
        return null;
    }

    private void flushHunkInto(FileBuilder file, List<HunkLine> hunkLines,
                               int oldStart, int newStart, int hunkOld, int hunkNew) {
        if (file == null || hunkLines == null || hunkLines.isEmpty()) {
            return;
        }
        file.hunks.add(new DiffHunk(file.oldPath, file.newPath,
                hunkOld, oldStart, hunkNew, newStart, List.copyOf(hunkLines)));
    }

    /** 文件构建中间态 */
    private static final class FileBuilder {
        String oldPath;
        String newPath;
        boolean isAdded;
        boolean isDeleted;
        boolean isRenamed;
        final List<DiffHunk> hunks = new ArrayList<>();

        DiffFile build(String defaultPath) {
            // 裸 hunk（无文件头）：用调用方给的兜底路径
            if (newPath == null && oldPath == null) {
                newPath = defaultPath;
            }
            String path = newPath != null ? newPath : oldPath;
            return new DiffFile(oldPath, newPath, isDeleted, isRenamed, isAdded,
                    DiffParser.detectLanguage(path), List.copyOf(hunks));
        }
    }
}
