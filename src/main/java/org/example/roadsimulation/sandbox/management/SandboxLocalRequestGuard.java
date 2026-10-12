package org.example.roadsimulation.sandbox.management;

import jakarta.servlet.http.*;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.springframework.web.servlet.HandlerInterceptor;
import java.net.URI;
import java.util.Set;

/** Local-only is not authentication. Reject LAN, DNS rebinding and browser simple-request writes. */
public final class SandboxLocalRequestGuard implements HandlerInterceptor {
    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!LOOPBACK.contains(request.getRemoteAddr())) deny();
        String host = request.getServerName();
        if (!Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(host)) deny();
        String origin = request.getHeader("Origin");
        if (origin != null) {
            try {
                URI value = URI.create(origin); int port = value.getPort() == -1 ? ("https".equals(value.getScheme()) ? 443 : 80) : value.getPort();
                if (!Set.of("http", "https").contains(value.getScheme())
                        || !Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(value.getHost())
                        || (port != 5173 && port != request.getServerPort()) || value.getUserInfo() != null
                        || (value.getPath() != null && !value.getPath().isEmpty()) || value.getQuery() != null || value.getFragment() != null) deny();
            } catch (IllegalArgumentException failure) { deny(); }
        }
        if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod()) && !"1".equals(request.getHeader("X-Sandbox-Request"))) deny();
        return true;
    }
    private static void deny() { throw new SandboxWorkspaceException("LOCAL_ACCESS_REQUIRED", "Sandbox API requires a trusted local request"); }
}
