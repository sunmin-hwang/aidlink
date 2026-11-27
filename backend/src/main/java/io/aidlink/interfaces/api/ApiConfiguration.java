package io.aidlink.interfaces.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiConfiguration implements WebMvcConfigurer {

    private final AnalysisRateLimitInterceptor rateLimitInterceptor;
    private final String[] allowedOrigins;

    public ApiConfiguration(
            AnalysisRateLimitInterceptor rateLimitInterceptor,
            @Value("${aidlink.cors.allowed-origins}") String allowedOrigins
    ) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.allowedOrigins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/v1/eligibility/**", "/api/v1/simulations/**", "/api/v1/me/briefings");
    }
}
