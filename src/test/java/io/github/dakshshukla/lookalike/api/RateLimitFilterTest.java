package io.github.dakshshukla.lookalike.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private final MutableClock clock = new MutableClock();

    @Test
    void rejectsRequestsOverTheLimitAndSaysWhenToRetry() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, clock);

        assertThat(call(filter, "10.0.0.1", "/v1/lookalikes").getStatus()).isEqualTo(200);
        assertThat(call(filter, "10.0.0.1", "/v1/lookalikes").getStatus()).isEqualTo(200);
        MockHttpServletResponse limited = call(filter, "10.0.0.1", "/v1/lookalikes");

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
        assertThat(limited.getContentAsString()).contains("2 requests per minute");
    }

    @Test
    void countsEachClientSeparately() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, clock);

        assertThat(call(filter, "10.0.0.1", "/v1/lookalikes").getStatus()).isEqualTo(200);
        assertThat(call(filter, "10.0.0.2", "/v1/lookalikes").getStatus()).isEqualTo(200);
        assertThat(call(filter, "10.0.0.1", "/v1/lookalikes").getStatus()).isEqualTo(429);
    }

    @Test
    void allowsAgainInTheNextWindow() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, clock);
        call(filter, "10.0.0.1", "/v1/lookalikes");
        assertThat(call(filter, "10.0.0.1", "/v1/lookalikes").getStatus()).isEqualTo(429);

        clock.advance(Duration.ofSeconds(61));

        assertThat(call(filter, "10.0.0.1", "/v1/lookalikes").getStatus()).isEqualTo(200);
    }

    @Test
    void leavesThePageAndHealthCheckAlone() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, clock);
        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, "10.0.0.1", "/").getStatus()).isEqualTo(200);
            assertThat(call(filter, "10.0.0.1", "/actuator/health").getStatus()).isEqualTo(200);
        }
    }

    private static MockHttpServletResponse call(RateLimitFilter filter, String client, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(client);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
