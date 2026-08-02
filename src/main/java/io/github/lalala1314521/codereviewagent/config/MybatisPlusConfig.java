package io.github.lalala1314521.codereviewagent.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p><b>必须注册 PaginationInnerInterceptor，否则 selectPage 不做物理分页</b>——
 * 这是 MyBatis-Plus 最经典的坑：不配置时分页对象返回的 records 是全表数据，
 * total 也是错的。插件通过拦截器改写 SQL，自动追加 LIMIT。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
