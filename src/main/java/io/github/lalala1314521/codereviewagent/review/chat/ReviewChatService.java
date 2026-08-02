package io.github.lalala1314521.codereviewagent.review.chat;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.lalala1314521.codereviewagent.api.dto.ReviewChatRequest;
import io.github.lalala1314521.codereviewagent.api.dto.ReviewChatResponse;
import io.github.lalala1314521.codereviewagent.common.exception.BizException;
import io.github.lalala1314521.codereviewagent.persistence.entity.ReviewFindingEntity;
import io.github.lalala1314521.codereviewagent.persistence.entity.ReviewRecordEntity;
import io.github.lalala1314521.codereviewagent.persistence.mapper.ReviewFindingMapper;
import io.github.lalala1314521.codereviewagent.persistence.mapper.ReviewRecordMapper;
import io.github.lalala1314521.codereviewagent.review.llm.ActiveProviderService;
import io.github.lalala1314521.codereviewagent.review.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 基于已落库审查记录和 Findings 的上下文问答服务。 */
@Service
public class ReviewChatService {

    private static final Logger log = LoggerFactory.getLogger(ReviewChatService.class);

    private final ReviewRecordMapper recordMapper;
    private final ReviewFindingMapper findingMapper;
    private final ActiveProviderService activeProviderService;
    private final ReviewChatPromptBuilder promptBuilder;

    public ReviewChatService(ReviewRecordMapper recordMapper,
                             ReviewFindingMapper findingMapper,
                             ActiveProviderService activeProviderService,
                             ReviewChatPromptBuilder promptBuilder) {
        this.recordMapper = recordMapper;
        this.findingMapper = findingMapper;
        this.activeProviderService = activeProviderService;
        this.promptBuilder = promptBuilder;
    }

    public ReviewChatResponse chat(Long reviewId, ReviewChatRequest request) {
        ReviewRecordEntity record = recordMapper.selectById(reviewId);
        if (record == null) {
            throw new BizException(404, "review record not found: " + reviewId);
        }
        List<ReviewFindingEntity> findings = findingMapper.selectList(
                new QueryWrapper<ReviewFindingEntity>()
                        .eq("review_record_id", reviewId)
                        .orderByAsc("FIELD(severity, 'ERROR', 'WARNING', 'INFO')")
                        .orderByAsc("file_path", "line_number"));

        LlmClient client = activeProviderService.getClient(request.provider());
        String provider = client.providerName();
        if (!activeProviderService.isAvailable(provider)) {
            throw new BizException(400, "Agent " + provider + " 未配置 API Key，暂不可用");
        }
        String agentName = StringUtils.hasText(request.agentName())
                ? request.agentName().trim() : defaultDisplayName(provider);
        String systemPrompt = promptBuilder.buildSystemPrompt(
                record, findings, agentName, request.instruction());
        String userPrompt = promptBuilder.buildUserPrompt(request.history(), request.message());

        log.info("review chat start reviewId={} provider={} history={}", reviewId, provider,
                request.history() == null ? 0 : request.history().size());
        String answer = client.chat(systemPrompt, userPrompt);
        log.info("review chat done reviewId={} provider={} answerChars={}",
                reviewId, provider, answer == null ? 0 : answer.length());
        return new ReviewChatResponse(answer, provider,
                activeProviderService.modelName(provider), agentName);
    }

    private String defaultDisplayName(String provider) {
        return switch (provider.toLowerCase()) {
            case "deepseek" -> "DeepSeek Review Agent";
            case "qianwen" -> "通义代码审查 Agent";
            case "openai" -> "OpenAI Review Agent";
            default -> "CodeReview Agent";
        };
    }
}
