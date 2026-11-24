package com.cqu.campus.common;

import com.cqu.campus.security.CurrentUser;
import com.cqu.campus.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * 为每个请求生成 traceId：写入 MDC、响应头，并记录访问日志
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "traceId";

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(MDC_KEY, traceId);
        response.setHeader("X-Trace-Id", traceId);
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            CurrentUser currentUser = UserContext.get();
            long latency = System.currentTimeMillis() - start;
            log.info("method={} path={} status={} latency={}ms traceId={} userId={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), latency, traceId,
                    currentUser == null ? "-" : currentUser.getUserId());
            UserContext.clear();
            MDC.remove(MDC_KEY);
        }
    }
}
