package com.xmen.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Filter that restricts incoming requests to either:
 * - loopback clients (native app calling http://localhost:PORT)
 * - or browsers with an Origin header matching the configured allowed origins.
 */
public class OriginRestrictionFilter extends OncePerRequestFilter {

    private final Set<String> allowedOrigins;

    public OriginRestrictionFilter(String allowedOriginsCsv) {
        if (StringUtils.hasText(allowedOriginsCsv)) {
            this.allowedOrigins = Arrays.stream(allowedOriginsCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toCollection(HashSet::new));
        } else {
            this.allowedOrigins = new HashSet<>();
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Always allow static resources under common paths; adjust if needed
        String path = request.getRequestURI();
        if (path.startsWith("/css/") || path.startsWith("/images/") || path.startsWith("/js/") || path.equals("/") || path.startsWith("/actuator/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Preflight: let CORS layer handle it if Origin is allowed; otherwise block
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            if (isAllowedOrigin(origin)) {
                filterChain.doFilter(request, response);
            } else {
                deny(response, "Blocked preflight from disallowed origin");
            }
            return;
        }

        // Allow loopback clients (native app)
        if (isLoopback(request.getRemoteAddr())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Allow if Origin header is present and allowed (frontend)
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (isAllowedOrigin(origin)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Otherwise, block
        deny(response, "Request blocked by origin policy");
    }

    private boolean isAllowedOrigin(String origin) {
        if (!StringUtils.hasText(origin)) {
            return false;
        }
        // Exact match against configured list
        return allowedOrigins.contains(origin);
    }

    private boolean isLoopback(String remoteAddr) {
        try {
            InetAddress addr = InetAddress.getByName(remoteAddr);
            return addr.isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private void deny(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }
}

