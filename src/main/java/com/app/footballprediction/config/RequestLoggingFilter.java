package com.app.footballprediction.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Logs every incoming HTTP request and outgoing response.
 * Runs as a Servlet filter — fires before and after every controller call.
 */
@Component
@Slf4j
public class RequestLoggingFilter implements Filter {

   @Override
   public void doFilter(ServletRequest request,
                        ServletResponse response,
                        FilterChain chain)
         throws IOException, ServletException {

      HttpServletRequest  httpReq  = (HttpServletRequest)  request;
      HttpServletResponse httpResp = (HttpServletResponse) response;

      long startTime = System.currentTimeMillis();

      // Log incoming request
      log.info("→ {} {} [from: {}]",
            httpReq.getMethod(),
            httpReq.getRequestURI(),
            httpReq.getRemoteAddr());

      // Pass through to controller
      chain.doFilter(request, response);

      // Log outgoing response with timing
      long duration = System.currentTimeMillis() - startTime;
      log.info("← {} {} → {} ({}ms)",
            httpReq.getMethod(),
            httpReq.getRequestURI(),
            httpResp.getStatus(),
            duration);
   }
}