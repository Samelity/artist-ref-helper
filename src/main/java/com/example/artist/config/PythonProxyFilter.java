package com.example.artist.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 把浏览器发往 /py/** 的请求转发到 Python 微服务，并去掉 /py 前缀。
 *
 * <p>生产环境只有 Spring Boot 对外暴露端口，图片代理、网络搜图等 Python 接口
 * 统一经此转发，前端因此可以使用相对路径、也无需配置 CORS。
 */
public class PythonProxyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PythonProxyFilter.class);

    private static final String PREFIX = "/py";

    /** 只放行前端实际使用的接口，避免把整个 Python 服务挂到公网入口上。 */
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/images/view",
            "/pixabay/search",
            "/pexels/search",
            "/remote/save");

    private static final Set<String> FORWARDED_REQUEST_HEADERS = Set.of(
            "accept", "accept-language", "content-type", "range",
            "if-none-match", "if-modified-since", "if-range");

    private static final Set<String> FORWARDED_RESPONSE_HEADERS = Set.of(
            "content-type", "content-length", "content-encoding", "content-disposition",
            "cache-control", "etag", "last-modified", "accept-ranges", "content-range");

    private static final int BUFFER_SIZE = 64 * 1024;

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public PythonProxyFilter(String baseUrl, Duration connectTimeout, Duration requestTimeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.requestTimeout = requestTimeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(PREFIX.length());
        if (!ALLOWED_PATHS.contains(path)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"未开放该接口\"}");
            return;
        }
        String query = request.getQueryString();
        URI target = URI.create(baseUrl + path + (query == null ? "" : "?" + query));

        HttpRequest.Builder builder = HttpRequest.newBuilder(target).timeout(requestTimeout);
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (FORWARDED_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                Enumeration<String> values = request.getHeaders(name);
                while (values.hasMoreElements()) {
                    builder.header(name, values.nextElement());
                }
            }
        }
        builder.method(request.getMethod(), requestBody(request));

        HttpResponse<InputStream> upstream;
        try {
            upstream = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writeBadGateway(response, "Python 服务请求被中断");
            return;
        } catch (IOException e) {
            log.warn("Python 服务不可用: {} {} - {}", request.getMethod(), target, e.toString());
            writeBadGateway(response, "Python 服务不可用");
            return;
        }

        response.setStatus(upstream.statusCode());
        for (Map.Entry<String, List<String>> header : upstream.headers().map().entrySet()) {
            if (!FORWARDED_RESPONSE_HEADERS.contains(header.getKey().toLowerCase(Locale.ROOT))) {
                continue;
            }
            boolean first = true;
            for (String value : header.getValue()) {
                if (first) {
                    response.setHeader(header.getKey(), value);
                    first = false;
                } else {
                    response.addHeader(header.getKey(), value);
                }
            }
        }

        try (InputStream in = upstream.body(); OutputStream out = response.getOutputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            // 浏览器提前取消下载（如切换图片）属于正常情况
            log.debug("Python 响应流中断: {}", target, e);
        }
    }

    private HttpRequest.BodyPublisher requestBody(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method)) {
            return HttpRequest.BodyPublishers.noBody();
        }
        long length = request.getContentLengthLong();
        if (length < 0) {
            return HttpRequest.BodyPublishers.ofInputStream(() -> uncheckedInputStream(request));
        }
        return HttpRequest.BodyPublishers.fromPublisher(
                HttpRequest.BodyPublishers.ofInputStream(() -> uncheckedInputStream(request)), length);
    }

    private static InputStream uncheckedInputStream(HttpServletRequest request) {
        try {
            return request.getInputStream();
        } catch (IOException e) {
            throw new IllegalStateException("读取请求体失败", e);
        }
    }

    private static void writeBadGateway(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
