package com.zdmj.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestTraceFilterTest {

    private final RequestTraceFilter filter = new RequestTraceFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void 连续请求_后端生成独立编号且不采用客户端编号_结束后清理日志上下文() throws Exception {
        var first = new MockHttpServletRequest("GET", "/first");
        first.addHeader("X-Trace-Id", "client-controlled-id");
        var firstResponse = new MockHttpServletResponse();
        MDC.put("otherKey", "preserved");

        filter.doFilter(first, firstResponse, (request, response) -> {
            assertThat(MDC.get("traceId")).matches("[0-9a-f]{32}");
            assertThat(firstResponse.getHeader("X-Trace-Id")).isEqualTo(MDC.get("traceId"));
        });
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("otherKey")).isEqualTo("preserved");

        var secondResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/second"), secondResponse,
                (request, response) -> assertThat(MDC.get("traceId"))
                        .isEqualTo(secondResponse.getHeader("X-Trace-Id")));
        assertThat(secondResponse.getHeader("X-Trace-Id"))
                .matches("[0-9a-f]{32}").isNotEqualTo(firstResponse.getHeader("X-Trace-Id"));
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void 下游异常_原异常继续传播_恢复进入前的日志上下文() {
        var failure = new IllegalStateException("test-failure");
        var response = new MockHttpServletResponse();
        MDC.put("traceId", "outer-trace");

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/failed"), response,
                (request, result) -> {
                    assertThat(MDC.get("traceId")).isEqualTo(response.getHeader("X-Trace-Id"));
                    throw failure;
                })).isSameAs(failure);

        assertThat(MDC.get("traceId")).isEqualTo("outer-trace");
    }

    @Test
    void 容器错误分发_复用原请求编号_结束后清理上下文() throws Exception {
        var request = new MockHttpServletRequest("GET", "/failed");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });
        String traceId = response.getHeader("X-Trace-Id");
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/failed");
        request.setRequestURI("/error");
        response.reset();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("traceId")).isEqualTo(traceId);
            assertThat(response.getHeader("X-Trace-Id")).isEqualTo(traceId);
        });

        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void 异步入口与再次分发_区分分发状态并复用原编号() throws Exception {
        var request = new MockHttpServletRequest("POST", "/stream");
        request.setAsyncSupported(true);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> request.startAsync(request, response));
        String traceId = response.getHeader("X-Trace-Id");
        assertThat(RequestTraceFilter.requireTraceId(request)).isEqualTo(traceId);
        assertThat(MDC.get("traceId")).isNull();
        request.setDispatcherType(DispatcherType.ASYNC);

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("traceId")).isEqualTo(traceId);
            request.getAsyncContext().complete();
        });

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(traceId);
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void 读取请求编号_缺少过滤器建立的编号时立即失败() {
        assertThatThrownBy(() -> RequestTraceFilter.requireTraceId(new MockHttpServletRequest()))
                .isInstanceOf(IllegalStateException.class).hasMessage("HTTP 请求缺少日志关联编号");
    }

    @Test
    void 并发请求_执行期间编号相互隔离_工作线程结束后没有残留() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2)
                    .mapToObj(index -> executor.submit(() -> {
                        var response = new MockHttpServletResponse();
                        filter.doFilter(new MockHttpServletRequest("GET", "/concurrent/" + index), response,
                                (request, result) -> {
                                    String traceId = MDC.get("traceId");
                                    try {
                                        barrier.await(5, TimeUnit.SECONDS);
                                    } catch (Exception exception) {
                                        throw new IllegalStateException(exception);
                                    }
                                    assertThat(MDC.get("traceId")).isEqualTo(traceId)
                                            .isEqualTo(response.getHeader("X-Trace-Id"));
                                });
                        assertThat(MDC.get("traceId")).isNull();
                        return response.getHeader("X-Trace-Id");
                    })).toList();
            assertThat(tasks.get(0).get(10, TimeUnit.SECONDS))
                    .isNotEqualTo(tasks.get(1).get(10, TimeUnit.SECONDS));
        }
    }
}
