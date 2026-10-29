package com.example.artist.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PythonProxyFilterTest {

    private HttpServer upstream;
    private final AtomicReference<String> received = new AtomicReference<>();
    private final AtomicReference<String> receivedRange = new AtomicReference<>();

    @BeforeEach
    void startUpstream() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.set(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + body);
            receivedRange.set(exchange.getRequestHeaders().getFirst("Range"));
            byte[] payload = ("upstream:" + body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain;charset=UTF-8");
            exchange.getResponseHeaders().add("X-Ignored", "nope");
            exchange.sendResponseHeaders(201, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        upstream.start();
    }

    @AfterEach
    void stopUpstream() {
        upstream.stop(0);
    }

    private PythonProxyFilter filter() {
        String baseUrl = "http://127.0.0.1:" + upstream.getAddress().getPort();
        return new PythonProxyFilter(baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    @Test
    void forwardsMethodPathQueryAndHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/py/images/view");
        request.setQueryString("path=a%2Fb.png");
        request.addHeader("Range", "bytes=0-10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, response, chain);

        assertThat(received.get()).isEqualTo("GET /images/view?path=a%2Fb.png ");
        assertThat(receivedRange.get()).isEqualTo("bytes=0-10");
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(response.getContentAsString()).isEqualTo("upstream:");
        assertThat(response.getHeader("X-Ignored")).isNull();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void streamsRequestBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/py/remote/save");
        request.setContentType("application/json");
        request.setContent("{\"url\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, new MockFilterChain());

        assertThat(received.get()).isEqualTo("POST /remote/save {\"url\":\"x\"}");
        assertThat(response.getContentAsString()).isEqualTo("upstream:{\"url\":\"x\"}");
    }

    @Test
    void returnsBadGatewayWhenUpstreamIsDown() throws Exception {
        PythonProxyFilter filter = filter();
        upstream.stop(0);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/py/pixabay/search"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getContentAsString()).contains("Python 服务不可用");
    }

    @Test
    void rejectsPathsOutsideTheWhitelist() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter().doFilter(new MockHttpServletRequest("POST", "/py/index"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(received.get()).isNull();
    }
}
