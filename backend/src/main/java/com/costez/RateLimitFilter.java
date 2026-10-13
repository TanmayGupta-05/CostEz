package com.costez;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-110)
public class RateLimitFilter extends OncePerRequestFilter {
  record Window(long minute, int hits) {}

  final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String path = req.getRequestURI();
    if (path.startsWith("/api/auth/")
        || path.startsWith("/api/pulse/")
        || ("POST".equals(req.getMethod()) && path.startsWith("/api/reports"))) {
      long minute = System.currentTimeMillis() / 60000;
      String key = req.getRemoteAddr() + ":" + (path.startsWith("/api/pulse/") ? "pulse" : path);
      if (windows.size() > 10000) windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
      Window value =
          windows.compute(
              key,
              (k, old) ->
                  old == null || old.minute() != minute
                      ? new Window(minute, 1)
                      : new Window(minute, old.hits() + 1));
      if (value.hits() > 40) {
        res.setStatus(429);
        res.setHeader("Retry-After", "60");
        res.setContentType("application/json");
        res.getWriter().write("{\"message\":\"Too many requests. Try again in a minute.\"}");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
