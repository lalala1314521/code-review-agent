package io.github.lalala1314521.codereviewagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lalala1314521.codereviewagent.review.ReviewEngine;
import io.github.lalala1314521.codereviewagent.review.llm.LlmClient;
import io.github.lalala1314521.codereviewagent.review.llm.LlmProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 评测启动器：启动时加参数 {@code --eval=true} 跑一次评测，输出到 eval/reports/ 后退出。
 *
 * <p>评测模式禁用 Web 环境（不启 Tomcat 避免端口冲突），只跑 LLM 调用。
 * 示例：{@code mvn spring-boot:run -Dspring-boot.run.arguments="--eval=true"}
 * 不带该参数时不做事，不影响正常启动。
 */
@Component
public class EvalCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalCommandLineRunner.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final ReviewEngine reviewEngine;
    private final ApplicationContext applicationContext;

    public EvalCommandLineRunner(LlmProviderFactory llmProviderFactory,
                                 ObjectMapper objectMapper,
                                 ReviewEngine reviewEngine,
                                 ApplicationContext applicationContext) {
        this.llmClient = llmProviderFactory.getDefault();
        this.objectMapper = objectMapper;
        this.reviewEngine = reviewEngine;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) throws Exception {
        boolean evalMode = false;
        for (String arg : args) {
            if ("--eval=true".equals(arg)) {
                evalMode = true;
                break;
            }
        }

        if (!evalMode) {
            log.debug("eval mode not enabled, skip. Use --eval=true to run eval.");
            return;
        }

        log.info("=== EvalRunner 启动 ===");
        EvalRunner runner = new EvalRunner(llmClient, objectMapper, reviewEngine);
        String report = runner.run();

        System.out.println("\n" + report + "\n");
        log.info("=== EvalRunner 完成 ===");

        // 评测完成后退出应用（不启动 Web 服务）
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }
}
