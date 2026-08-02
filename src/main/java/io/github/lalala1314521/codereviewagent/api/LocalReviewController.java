package io.github.lalala1314521.codereviewagent.api;

import io.github.lalala1314521.codereviewagent.common.api.ApiResponse;
import io.github.lalala1314521.codereviewagent.common.exception.BizException;
import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.review.ReviewTriggerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 本地文件 MR 审查 API：没有 GitLab 也能体验完整审查（规则 → LLM → 裁决 → 落库 → SSE 进度）。
 *
 * <p>两种输入（按 fileName 后缀自动识别）：{@code .diff/.patch} 按 unified diff 原文审查（支持多文件）；
 * 其他后缀视为新增文件，自动包装为 new-file diff——输入格式与生产链路完全一致。
 *
 * <p>与 {@code DemoController}（dev-only）共用 {@link ReviewTriggerService} 编排，本端点为产品化版本（所有 profile 可用）。
 */
@RestController
@RequestMapping("/api/v1/reviews")
public class LocalReviewController {

    private static final Logger log = LoggerFactory.getLogger(LocalReviewController.class);
    private static final int MAX_CONTENT_CHARS = 200_000;   // 防超大文件打爆 LLM token 预算

    private final ReviewTriggerService triggerService;

    public LocalReviewController(ReviewTriggerService triggerService) {
        this.triggerService = triggerService;
    }

    /**
     * 触发本地文件审查。
     *
     * @return recordId（前端据此选中记录、挂 SSE 看实时进度）
     */
    @PostMapping("/local")
    public ApiResponse<Map<String, Object>> reviewLocal(@RequestBody LocalReviewRequest req) {
        if (req == null || !StringUtils.hasText(req.content())) {
            throw new BizException(400, "文件内容不能为空");
        }
        if (!StringUtils.hasText(req.fileName())) {
            throw new BizException(400, "fileName 不能为空（用于识别语言与 diff 类型）");
        }
        if (req.content().length() > MAX_CONTENT_CHARS) {
            throw new BizException(400, "文件过大（>" + MAX_CONTENT_CHARS / 1000 + "k 字符），请分段提交");
        }

        String fileName = req.fileName().trim();
        String diff = isDiffFile(fileName) ? req.content() : wrapAsNewFileDiff(fileName, req.content());

        long ts = System.currentTimeMillis();
        UnifiedMergeRequest mr = new UnifiedMergeRequest(
                "LOCAL", 0L,
                "local/" + fileName,
                ts % 100000,
                "local-" + ts,
                "local/" + fileName, "main",
                StringUtils.hasText(req.title()) ? req.title().trim() : "本地审查: " + fileName,
                null, "本地用户", "local",
                null, diff
        );

        Long recordId = triggerService.trigger(mr, "本地审查，未回写评论");
        log.info("local file review accepted recordId={} file={} diffChars={}", recordId, fileName, diff.length());
        return ApiResponse.ok(Map.of("recordId", recordId));
    }

    private boolean isDiffFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".diff") || lower.endsWith(".patch");
    }

    /**
     * 源代码文件 → new-file diff（unified diff 格式，全部行 ADD）。
     *
     * <p>为什么包装而不是直接喂 LLM：保证本地审查与生产链路<b>输入同构</b>——
     * DiffParser、规则引擎、行号体系、prompt 格式零差异，本地验证过的行为就是线上行为。
     */
    private String wrapAsNewFileDiff(String fileName, String content) {
        String[] lines = content.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        sb.append("diff --git a/").append(fileName).append(" b/").append(fileName).append('\n');
        sb.append("new file mode 100644\n");
        sb.append("--- /dev/null\n");
        sb.append("+++ b/").append(fileName).append('\n');
        sb.append("@@ -0,0 +1,").append(lines.length).append(" @@\n");
        for (String line : lines) {
            // 去掉行尾 \r（Windows 文件）
            sb.append('+').append(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line).append('\n');
        }
        return sb.toString();
    }

    /**
     * 本地审查请求体。
     *
     * @param fileName 文件名（带后缀，用于语言识别与 diff/源码判断）
     * @param content  文件内容（diff 原文或源代码全文）
     * @param title    可选标题（默认"本地审查: {fileName}"）
     */
    public record LocalReviewRequest(String fileName, String content, String title) {}
}
