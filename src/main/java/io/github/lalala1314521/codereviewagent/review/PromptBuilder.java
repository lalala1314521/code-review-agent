package io.github.lalala1314521.codereviewagent.review;

import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import org.springframework.stereotype.Component;

/**
 * Prompt 构造器：把 MR 元信息 + diff 拼成 LLM 能理解的 prompt。
 * System Prompt 定角色 + 输出格式；User Prompt 定上下文 + diff。
 */
@Component
public class PromptBuilder {

    /**
     * 系统提示词：定角色 + 输出要求（资深审查员、Markdown 报告、不夸大不复述）。
     */
    public String buildSystemPrompt() {
        return """
                你是一名资深代码审查员。请审查下面的代码变更，输出一份 Markdown 格式的审查报告。

                ## 输出格式

                ```
                ## 总体结论

                <一句话结论：建议合并 / 需修复 / 阻塞>

                ## 具体问题

                ### [严重度] 问题标题

                - **文件**: <文件路径>
                - **行号**: <行号或"全文">
                - **严重度**: ERROR / WARNING / INFO
                - **说明**: <问题原因>
                - **建议**: <修复建议，可附代码片段>
                ```

                ## 审查重点

                1. **安全**：SQL 注入、XSS、硬编码密钥、敏感信息日志
                2. **正确性**：空指针、异常吞掉、事务边界、并发问题
                3. **性能**：N+1 查询、不必要的循环、资源泄漏
                4. **可维护性**：魔法数字、超长函数、命名问题

                ## 约束

                - 不要复述代码内容
                - 不要夸大问题，无问题就说"未发现严重问题，建议合并"
                - 中文回答，简洁直接
                - 每个问题都要有具体的文件和行号
                """;
    }

    /**
     * 用户提示词：MR 元信息 + diff。
     *
     * <p>包含 MR 标题、分支、提交人，让 LLM 有上下文。
     * diff 文本可能很长，MVP 阶段不切分（V1 起 hunk 分批控制 token）。
     */
    public String buildUserPrompt(UnifiedMergeRequest mr) {
        return """
                ## MR 信息

                - 标题: %s
                - 分支: %s → %s
                - 提交人: %s (%s)
                - Commit: %s

                ## Diff

                ```diff
                %s
                ```
                """.formatted(
                mr.title(),
                mr.sourceBranch(),
                mr.targetBranch(),
                mr.authorName(),
                mr.authorUsername(),
                mr.commitSha(),
                mr.diff()
        );
    }
}
