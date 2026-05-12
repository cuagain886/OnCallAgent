package org.example.config;

import org.example.auth.AuthService;
import org.example.auth.JwtAuthFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安全配置：注册 JWT 过滤器、初始化默认管理员
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private AuthService authService;

    @Value("${security.admin.username:admin}")
    private String adminUsername;

    @Value("${security.admin.password:admin123}")
    private String adminPassword;

    /**
     * 注册 JWT 认证过滤器
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }

    /**
     * 初始化默认管理员账户（Redis 可用时才执行）
     */
    @Bean
    public boolean initDefaultAdmin() {
        try {
            authService.initDefaultAdmin(adminUsername, adminPassword);
        } catch (Exception e) {
            log.warn("Redis 未就绪，跳过默认管理员初始化: {}", e.getMessage());
        }
        return true;
    }
}
