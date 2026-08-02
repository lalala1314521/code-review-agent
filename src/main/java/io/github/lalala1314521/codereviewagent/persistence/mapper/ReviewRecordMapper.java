package io.github.lalala1314521.codereviewagent.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.lalala1314521.codereviewagent.persistence.entity.ReviewRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * review_record Mapper。
 *
 * <p>继承 MyBatis-Plus BaseMapper，自带 insert/updateById/selectById/
 * selectList(QueryWrapper)/selectPage 等 CRUD，无需写 XML。
 * 复杂聚合查询（KPI）用 QueryWrapper 在 Service 层组装。
 */
@Mapper
public interface ReviewRecordMapper extends BaseMapper<ReviewRecordEntity> {

    /**
     * 仓库分支树聚合（上下文切换器数据源）。
     *
     * <p>按 platform / repo_path / source_branch 三级分组统计 MR 数——
     * 前端据此渲染"远程（GITLAB）/ 本地（LOCAL）仓库 → 分支"两级选择器。
     */
    @Select("""
            SELECT platform, repo_path AS repoPath, source_branch AS branch,
                   MAX(project_id) AS projectId, COUNT(*) AS mrCount
            FROM review_record
            WHERE repo_path IS NOT NULL AND repo_path != ''
            GROUP BY platform, repo_path, source_branch
            ORDER BY platform, repo_path, mrCount DESC
            """)
    List<Map<String, Object>> selectRepoBranchTree();
}
