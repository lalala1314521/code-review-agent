package io.github.lalala1314521.codereviewagent.model;

/**
 * 统一 MR 模型（与平台无关）：Platform Adapter 把 GitLab/GitHub payload 归一到此，
 * 后续 Review 逻辑只认它，不关心平台差异。
 *
 * @param platform       GITLAB / GITHUB
 * @param projectId      GitLab project.id（数字 ID，调 API 用）
 * @param repoPath       group/repo（展示用）
 * @param mrIid          MR 内部编号（GitLab iid）
 * @param commitSha      触发审查的 commit SHA
 * @param sourceBranch   源分支
 * @param targetBranch   目标分支
 * @param title          MR 标题
 * @param description    MR 描述
 * @param authorName     提交人姓名
 * @param authorUsername 提交人用户名
 * @param webUrl         MR 在平台上的链接
 * @param diff           由 getMrDiff() 填充的 unified diff 文本
 */
public record UnifiedMergeRequest(
        String platform,
        Long projectId,
        String repoPath,
        Long mrIid,
        String commitSha,
        String sourceBranch,
        String targetBranch,
        String title,
        String description,
        String authorName,
        String authorUsername,
        String webUrl,
        String diff
) {}
