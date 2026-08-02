package io.github.lalala1314521.codereviewagent.webhook;

import io.github.lalala1314521.codereviewagent.config.GitLabProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

/**
 * Webhook 签名验证器。
 *
 * <p>GitLab 用固定 Secret Token 直接比对（{@code X-Gitlab-Token} header）。
 * 不用 {@code equals()} 直接比，而是 MD5 hex 后比较：防止时序攻击爆破 secret。
 *
 * @see <a href="https://docs.gitlab.com/ee/user/project/integrations/webhooks.html">GitLab Webhook</a>
 */
@Component
public class SignatureVerifier {

    private final GitLabProperties props;

    public SignatureVerifier(GitLabProperties props) {
        this.props = props;
    }

    /**
     * 验证 GitLab webhook 的 X-Gitlab-Token header。
     *
     * @param token 请求头里的 X-Gitlab-Token 值，可能为 null
     * @return true 验签通过；false 不通过
     */
    public boolean verifyGitLabToken(String token) {
        if (token == null || props.webhookSecret() == null) {
            return false;
        }
        // 用 MD5 hex 比较代替直接 String.equals，防时序攻击
        String expectedMd5 = DigestUtils.md5DigestAsHex(
                props.webhookSecret().getBytes(StandardCharsets.UTF_8));
        String actualMd5 = DigestUtils.md5DigestAsHex(
                token.getBytes(StandardCharsets.UTF_8));
        return expectedMd5.equals(actualMd5);
    }
}
