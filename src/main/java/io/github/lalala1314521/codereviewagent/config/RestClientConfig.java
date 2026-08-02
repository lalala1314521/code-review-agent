package io.github.lalala1314521.codereviewagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * RestClient 配置。
 *
 * <p>Spring 6 推荐用 RestClient 替代 RestTemplate，API 更现代。
 *
 * <p>GitLab 用统一的 bean（全应用共享一个 GitLab 实例）。
 * LLM 的 RestClient 不再用 bean——多 Provider 可插拔后，
 * 每个 Provider 在自己内部创建 RestClient（不同 base-url + api-key），
 * 由 {@code LlmProviderFactory} 统一管理。
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient gitLabRestClient(GitLabProperties props) {
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("PRIVATE-TOKEN", props.token())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /**
     * GitHub API client（token 为空也能构建——未配置时调用会 401，
     * 只有真实触发 GitHub 链路才需要配置，不影响应用启动）。
     */
    @Bean
    public RestClient gitHubRestClient(GitHubProperties props) {
        return RestClient.builder()
                .baseUrl(props.apiUrl())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeaders(headers -> {
                    if (props.token() != null && !props.token().isBlank()) {
                        headers.setBearerAuth(props.token());
                    }
                })
                .build();
    }
}
