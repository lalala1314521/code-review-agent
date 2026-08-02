package io.github.lalala1314521.codereviewagent.api;

import io.github.lalala1314521.codereviewagent.api.dto.ReviewChatRequest;
import io.github.lalala1314521.codereviewagent.api.dto.ReviewChatResponse;
import io.github.lalala1314521.codereviewagent.common.api.ApiResponse;
import io.github.lalala1314521.codereviewagent.review.chat.ReviewChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 审查上下文对话 API。 */
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewChatController {

    private final ReviewChatService reviewChatService;

    public ReviewChatController(ReviewChatService reviewChatService) {
        this.reviewChatService = reviewChatService;
    }

    @PostMapping("/{id}/chat")
    public ApiResponse<ReviewChatResponse> chat(@PathVariable Long id,
                                                @Valid @RequestBody ReviewChatRequest request) {
        return ApiResponse.ok(reviewChatService.chat(id, request));
    }
}
