package io.github.lalala1314521.codereviewagent.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.lalala1314521.codereviewagent.common.api.ApiResponse;
import io.github.lalala1314521.codereviewagent.common.api.PageResult;
import io.github.lalala1314521.codereviewagent.common.exception.BizException;
import io.github.lalala1314521.codereviewagent.persistence.entity.ReviewFindingEntity;
import io.github.lalala1314521.codereviewagent.persistence.entity.ReviewRecordEntity;
import io.github.lalala1314521.codereviewagent.persistence.mapper.ReviewFindingMapper;
import io.github.lalala1314521.codereviewagent.persistence.mapper.ReviewRecordMapper;
import io.github.lalala1314521.codereviewagent.review.progress.ReviewProgressPublisher;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * MR 审查队列 API（方案设计 12.3 / 12.4 / 12.7）。
 *
 * <p>GET /api/v1/reviews                  分页查询（status / conclusion 筛选）
 * GET /api/v1/reviews/{id}               审查记录详情
 * GET /api/v1/reviews/{id}/findings      记录 + 全部发现（详情页）
 * GET /api/v1/reviews/{id}/stream        SSE 推送 Agent 思考流（审查进度）
 */
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewRecordMapper recordMapper;
    private final ReviewFindingMapper findingMapper;
    private final ReviewProgressPublisher progressPublisher;

    public ReviewController(ReviewRecordMapper recordMapper, ReviewFindingMapper findingMapper,
                            ReviewProgressPublisher progressPublisher) {
        this.recordMapper = recordMapper;
        this.findingMapper = findingMapper;
        this.progressPublisher = progressPublisher;
    }

    /**
     * 分页查询审查列表（默认按触发时间倒序，最新在前）。
     *
     * @param repoPath 仓库路径精确匹配（上下文切换器）
     * @param branch   源分支精确匹配（上下文切换器）
     */
    @GetMapping
    public ApiResponse<PageResult<ReviewRecordEntity>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String conclusion,
            @RequestParam(required = false) String repoPath,
            @RequestParam(required = false) String branch) {

        if (size > 100) {
            size = 100;   // 防大分页拖垮 DB
        }
        QueryWrapper<ReviewRecordEntity> qw = new QueryWrapper<ReviewRecordEntity>()
                .eq(StringUtils.hasText(status), "status", status)
                .eq(StringUtils.hasText(conclusion), "conclusion", conclusion)
                .eq(StringUtils.hasText(repoPath), "repo_path", repoPath)
                .eq(StringUtils.hasText(branch), "source_branch", branch)
                .orderByDesc("triggered_at");

        Page<ReviewRecordEntity> result = recordMapper.selectPage(new Page<>(page, size), qw);
        return ApiResponse.ok(PageResult.of(result.getCurrent(), result.getSize(),
                result.getTotal(), result.getRecords()));
    }

    /**
     * 审查记录详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<ReviewRecordEntity> detail(@PathVariable Long id) {
        ReviewRecordEntity entity = recordMapper.selectById(id);
        if (entity == null) {
            throw new BizException(404, "review record not found: " + id);
        }
        return ApiResponse.ok(entity);
    }

    /**
     * 记录 + 全部发现（详情页一次拿全，finding 按严重度排序 ERROR 在前）。
     */
    @GetMapping("/{id}/findings")
    public ApiResponse<Map<String, Object>> findings(@PathVariable Long id) {
        ReviewRecordEntity entity = recordMapper.selectById(id);
        if (entity == null) {
            throw new BizException(404, "review record not found: " + id);
        }
        List<ReviewFindingEntity> findings = findingMapper.selectList(
                new QueryWrapper<ReviewFindingEntity>()
                        .eq("review_record_id", id)
                        .orderByAsc("FIELD(severity, 'ERROR', 'WARNING', 'INFO')")
                        .orderByAsc("file_path", "line_number"));
        return ApiResponse.ok(Map.of("reviewRecord", entity, "findings", findings));
    }

    /**
     * SSE 推送 Agent 思考流（方案设计 12.7）。
     *
     * <p>连接后先回放该记录已发生的全部事件（审查完成后再打开详情页
     * 也能看到完整过程），随后实时推送新事件；终态（DONE/FAILED）自动关闭。
     *
     * <p>注意：SSE 走 text/event-stream 长连接，<b>不走统一响应结构</b>
     * （ApiResponse 包裹会破坏协议），这是统一响应约定的合理例外。
     */
    @GetMapping(path = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id) {
        if (recordMapper.selectById(id) == null) {
            throw new BizException(404, "review record not found: " + id);
        }
        return progressPublisher.subscribe(id);
    }
}
