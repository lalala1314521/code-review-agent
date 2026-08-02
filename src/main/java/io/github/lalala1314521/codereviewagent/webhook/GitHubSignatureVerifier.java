package io.github.lalala1314521.codereviewagent.webhook;

import io.github.lalala1314521.codereviewagent.config.GitHubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * GitHub webhook 签名验证器（X-Hub-Signature-256）。
 *
 * <p>与 GitLab 静态 token 的区别：GitHub 用 HMAC-SHA256，凭证从不传输，
 * 传的是"密钥对请求体的签名"——抓到签名无法反推密钥，且签名绑定消息内容无法重放。
 *
 * <p>实现要点：比较用 {@link MessageDigest#isEqual}（恒定时间防时序攻击）；
 * 验签需要原始请求体——Controller 用 {@code @RequestBody String} 先收原文再反序列化。
 */
@Component
public class GitHubSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(GitHubSignatureVerifier.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final GitHubProperties props;

    public GitHubSignatureVerifier(GitHubProperties props) {
        this.props = props;
    }

    /**
     * 验证 X-Hub-Signature-256 签名。
     *
     * @param rawBody   原始请求体（未反序列化）
     * @param signature 请求头 X-Hub-Signature-256（形如 sha256=xxx）
     */
    public boolean verify(String rawBody, String signature) {
        if (rawBody == null || signature == null || !signature.startsWith(SIGNATURE_PREFIX)
                || props.webhookSecret() == null || props.webhookSecret().isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(props.webhookSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] actual = HexFormat.of().parseHex(signature.substring(SIGNATURE_PREFIX.length()));
            boolean ok = MessageDigest.isEqual(expected, actual);
            if (!ok) {
                log.warn("github webhook signature mismatch");
            }
            return ok;
        } catch (Exception e) {
            log.error("github webhook signature verify error: {}", e.getMessage());
            return false;
        }
    }
}
