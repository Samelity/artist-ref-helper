package com.example.artist.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Duration;

/**
 * 注册 Python 反向代理：浏览器访问 /py/** 等同访问 Python 服务的 /**。
 */
@Configuration
public class PythonProxyConfig {

    @Bean
    public FilterRegistrationBean<PythonProxyFilter> pythonProxyFilter(
            @Value("${clip.service.base-url}") String baseUrl,
            @Value("${clip.service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${clip.service.proxy-timeout-seconds:60}") long proxyTimeoutSeconds) {
        FilterRegistrationBean<PythonProxyFilter> registration = new FilterRegistrationBean<>(
                new PythonProxyFilter(
                        baseUrl,
                        Duration.ofMillis(connectTimeoutMs),
                        Duration.ofSeconds(proxyTimeoutSeconds)));
        registration.addUrlPatterns("/py/*");
        registration.setName("pythonProxyFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
