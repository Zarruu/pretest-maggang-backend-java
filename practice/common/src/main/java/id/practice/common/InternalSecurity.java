package id.practice.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalSecurity extends OncePerRequestFilter {
    private final byte[] key;
    public InternalSecurity(@Value("${app.internal-key}") String key) { this.key = key.getBytes(StandardCharsets.UTF_8); }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (req.getRequestURI().startsWith("/internal/")) {
            String supplied = req.getHeader("X-Internal-Key");
            if (supplied == null || !MessageDigest.isEqual(key, supplied.getBytes(StandardCharsets.UTF_8))) {
                res.sendError(403, "Invalid internal service key"); return;
            }
        }
        chain.doFilter(req, res);
    }
}
