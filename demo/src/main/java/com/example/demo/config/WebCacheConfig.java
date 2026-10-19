package com.example.demo.config;

import org.hibernate.annotations.DialectOverride;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.WebContentInterceptor;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebCacheConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        WebContentInterceptor interceptor = new WebContentInterceptor();
        interceptor.addCacheMapping(
                CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic(),
                "/api/events/**");
        registry.addInterceptor(interceptor);
    }
    //CORS 설정 추가하여 허용된 프론트엔드 도메인만 접속할수 있도록
    @Override
    public void addCorsMappings(CorsRegistry registry){
        registry.addMapping("/api/**")
                .allowedOrigins("https://qwmvkdid.duckdns.org")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}