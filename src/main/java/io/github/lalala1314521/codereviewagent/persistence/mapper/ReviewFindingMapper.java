package io.github.lalala1314521.codereviewagent.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.lalala1314521.codereviewagent.persistence.entity.ReviewFindingEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * review_finding Mapper。批量插入用 ServiceImpl 的 saveBatch（JDBC batch）。
 */
@Mapper
public interface ReviewFindingMapper extends BaseMapper<ReviewFindingEntity> {
}
