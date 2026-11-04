package com.example.artist.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * CLIP Python 微服务通信配置。
 */
@Configuration
public class ClipConfig {

    @Bean
    public WebClient clipWebClient(
            @Value("${clip.service.base-url}") String baseUrl,
            @Value("${clip.service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${clip.service.read-timeout-ms:30000}") int readTimeoutMs) {
        return buildWebClient(baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    /**
     * 相似图扫描专用 WebClient：图库较大时同步扫描会超过普通搜索的读超时。
     */
    @Bean
    public WebClient duplicatesWebClient(
            @Value("${clip.service.base-url}") String baseUrl,
            @Value("${clip.service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${image.duplicates.timeout-seconds:300}") long scanTimeoutSeconds) {
        return buildWebClient(baseUrl, connectTimeoutMs, (int) (scanTimeoutSeconds * 1000));
    }

    private static WebClient buildWebClient(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(readTimeoutMs))
                .doOnConnected(conn ->
                        conn.addHandlerLast(
                                new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}