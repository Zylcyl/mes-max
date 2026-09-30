package com.mesmax.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 装配：鉴权拦截器 + CORS。
 *
 * <p>关于 CORS：前端在开发态走 Vite 代理（{@code /api} → 8080）、部署态走 Nginx 反代，
 * 两种形态都是同源请求，本来不需要 CORS。这里保留一小段显式白名单只是为了方便
 * 「前后端分开起、直接用 5173 访问 8080」的调试姿势 —— 用
 * {@code allowedOriginPatterns("*") + allowCredentials(true)} 放开全世界的写法，
 * 等于允许任意网站带着用户凭据调用本系统接口，属于必须收敛的默认值。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    /** 允许的跨域来源（逗号分隔）。留空 = 完全不开放跨域。 */
    @Value("${mes-max.cors.allowed-origin-patterns:http://localhost:5173,http://127.0.0.1:5173,http://localhost:4173,http://127.0.0.1:4173}")
    private String allowedOriginPatterns;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/captcha",
                        "/error",
                        // H2 控制台仅在 h2 profile 下被自动装配（mysql/prod profile 不会开启），
                        // 这里放行是为了答辩现场直接查库；生产部署请勿启用 h2 profile。
                        "/h2-console/**"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] patterns = allowedOriginPatterns == null ? new String[0]
                : java.util.Arrays.stream(allowedOriginPatterns.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        if (patterns.length == 0) {
            // 未配置来源清单：不注册任何 CORS 映射（同源访问不受影响）
            return;
        }
        registry.addMapping("/**")
                .allowedOriginPatterns(patterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
