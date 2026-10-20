package io.github.dakshshukla.lookalike.api;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window request limit per client address for the API paths, kept in memory. It is meant
 * for a single small instance behind a proxy. The address is {@code getRemoteAddr()}, which the
 * servlet container only replaces with X-Forwarded-For when the request came through a trusted
 * proxy, so a client cannot dodge the limit by sending its own header.
 */
@Component
@ConditionalOnProperty(name = "lookalike.ratelimit.enabled", havingValue = "true")
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int perMinute;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitFilter(@Value("${lookalike.ratelimit.per-minute:30}") int perMinute) {
        this(perMinute, Clock.systemUTC());
    }

    RateLimitFilter(int perMinute, Clock clock) {
        this.perMinute = Math.max(1, perMinute);
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(w -> now - w.start >= WINDOW_MILLIS);
        }
        Window window = windows.compute(request.getRemoteAddr(), (key, current) ->
                current == null || now - current.start >= WINDOW_MILLIS ? new Window(now, 1) : new Window(current.start, current.count + 1));

        if (window.count > perMinute) {
            long retryAfter = Math.max(1, (window.start + WINDOW_MILLIS - now + 999) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\",\"detail\":\"This demo allows "
                    + perMinute + " requests per minute per client. Try again in " + retryAfter + " seconds.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private record Window(long start, int count) {
    }
}
