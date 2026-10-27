package com.blooddonation.spring.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AdminInterceptor adminInterceptor;

    @Autowired
    private PortalAuthInterceptor portalAuthInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(portalAuthInterceptor)
                .addPathPatterns("/portal/**");
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/login", "/admin/logout", "/css/**", "/js/**");
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Real-world: long browser cache for fingerprinted/static assets.
        // Images/CSS/JS change rarely — 365d cache + must-revalidate keeps repeat visits instant.
        CacheControl year = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().mustRevalidate();
        registry.addResourceHandler("/images/**").addResourceLocations("classpath:/static/images/")
                .setCacheControl(year);
        registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/")
                .setCacheControl(year);
        registry.addResourceHandler("/js/**").addResourceLocations("classpath:/static/js/")
                .setCacheControl(year);
    }
}
