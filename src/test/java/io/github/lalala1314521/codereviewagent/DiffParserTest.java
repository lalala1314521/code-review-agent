package io.github.lalala1314521.codereviewagent;

import io.github.lalala1314521.codereviewagent.model.DiffFile;
import io.github.lalala1314521.codereviewagent.review.diff.DiffParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DiffParser 解析测试：unified diff → 文件/行号/新增行。
 */
class DiffParserTest {

    private final DiffParser parser = new DiffParser();

    @Test
    void parsesFileAndHunkLines() {
        String diff = """
                diff --git a/src/UserService.java b/src/UserService.java
                index 123..456 100644
                --- a/src/UserService.java
                +++ b/src/UserService.java
                @@ -10,3 +10,4 @@ public class UserService {
                 import java.util.List;
                +import java.util.Map;
                 public String hello() {
                +    return "hi";
                 }
                """;

        List<DiffFile> files = parser.parse(diff);
        assertEquals(1, files.size());
        DiffFile file = files.get(0);
        assertEquals("src/UserService.java", file.newPath());
        assertEquals("java", file.language());
        // 新增行 2 条（第 11、13 行），上下文行不计数
        assertEquals(2, file.addedLines().size());
        assertTrue(file.addedLines().stream().anyMatch(l -> l.newLineNumber() == 11
                && l.content().contains("Map")));
        assertTrue(file.addedLines().stream().anyMatch(l -> l.newLineNumber() == 13
                && l.content().contains("return")));
    }

    @Test
    void multiFileDiff() {
        String diff = """
                diff --git a/a.java b/a.java
                --- a/a.java
                +++ b/a.java
                @@ -1 +1,2 @@
                -old
                +new1
                +new2
                diff --git a/b.txt b/b.txt
                --- a/b.txt
                +++ b/b.txt
                @@ -1 +1 @@
                -x
                +y
                """;
        List<DiffFile> files = parser.parse(diff);
        assertEquals(2, files.size());
        assertEquals("a.java", files.get(0).newPath());
        assertEquals("b.txt", files.get(1).newPath());
        assertEquals("java", files.get(0).language());
    }
}
