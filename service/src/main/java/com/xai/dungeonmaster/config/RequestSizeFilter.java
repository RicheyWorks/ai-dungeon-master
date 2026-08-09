package com.xai.dungeonmaster.config;

import com.xai.dungeonmaster.auth.RateLimitFilter;
import com.xai.dungeonmaster.auth.SecurityAudit;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Early reject of oversized non-multipart requests.
 * Checks {@code Content-Length} when present and always wraps the body stream
 * so chunked / missing-length bodies cannot bypass the cap.
 * Multipart pack uploads keep their own Spring multipart limits.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestSizeFilter extends OncePerRequestFilter {

    private static final Set<String> SKIP_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final boolean enabled;
    private final long maxBytes;

    public RequestSizeFilter(
            @Value("${game.http.max-request-bytes:1048576}") long maxBytes,
            @Value("${game.http.max-request-enabled:true}") boolean enabled) {
        this.maxBytes = Math.max(256L, maxBytes);
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) return true;
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        if (SKIP_METHODS.contains(method)) return true;
        String ct = request.getContentType();
        if (ct != null && ct.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            return true; // handled by spring.servlet.multipart.*
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long len = req.getContentLengthLong();
        if (len > maxBytes) {
            writeTooLarge(req, res, "contentLength=" + len);
            return;
        }
        AtomicBoolean oversize = new AtomicBoolean(false);
        LimitedRequest wrapped = new LimitedRequest(req, maxBytes, oversize);
        try {
            chain.doFilter(wrapped, res);
        } catch (Throwable e) {
            if (oversize.get() || isOversizedCause(e)) {
                if (!res.isCommitted()) {
                    writeTooLarge(req, res, "streamBytes>" + maxBytes);
                }
                return;
            }
            if (e instanceof IOException io) throw io;
            if (e instanceof ServletException se) throw se;
            if (e instanceof RuntimeException re) throw re;
            if (e instanceof Error err) throw err;
            throw new ServletException(e);
        }
        if (oversize.get() && !res.isCommitted()) {
            writeTooLarge(req, res, "streamBytes>" + maxBytes);
        }
    }

    private void writeTooLarge(HttpServletRequest req, HttpServletResponse res, String detail)
            throws IOException {
        String requestId = safeRequestId(req);
        String path = req.getRequestURI() == null ? "-" : req.getRequestURI();
        SecurityAudit.log(
                "request_too_large",
                path,
                RateLimitFilter.clientIp(req, false),
                requestId,
                detail + " maxBytes=" + maxBytes);
        res.resetBuffer();
        res.setStatus(413);
        res.setContentType("application/json");
        res.getWriter().write("{\"type\":\"error\",\"version\":1,\"payload\":{\"message\":"
                + "\"Request body too large (max " + maxBytes + " bytes).\"},"
                + "\"requestId\":\"" + requestId + "\"}");
        res.flushBuffer();
    }

    private static boolean isOversizedCause(Throwable t) {
        while (t != null) {
            if (t instanceof OversizedBodyException) return true;
            t = t.getCause();
        }
        return false;
    }

    private static String safeRequestId(HttpServletRequest req) {
        String id = RequestIdFilter.resolve(req);
        if (id == null || id.isBlank()) return "";
        return id.replace("\"", "").replace("\\", "");
    }

    /** Package-visible for unit tests. */
    static final class OversizedBodyException extends IOException {
        OversizedBodyException(long maxBytes) {
            super("Request body exceeded max " + maxBytes + " bytes");
        }
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long maxBytes;
        private final AtomicBoolean oversize;
        private ServletInputStream limited;

        LimitedRequest(HttpServletRequest request, long maxBytes, AtomicBoolean oversize) {
            super(request);
            this.maxBytes = maxBytes;
            this.oversize = oversize;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (limited == null) {
                limited = new LimitedServletInputStream(super.getInputStream(), maxBytes, oversize);
            }
            return limited;
        }

        @Override
        public java.io.BufferedReader getReader() throws IOException {
            String enc = getCharacterEncoding();
            if (enc == null) enc = "UTF-8";
            return new java.io.BufferedReader(
                    new java.io.InputStreamReader(getInputStream(), enc));
        }
    }

    /** Package-visible for unit tests. */
    static final class LimitedServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long maxBytes;
        private final AtomicBoolean oversize;
        private final AtomicLong read = new AtomicLong();

        LimitedServletInputStream(ServletInputStream delegate, long maxBytes, AtomicBoolean oversize) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
            this.oversize = oversize;
        }

        private void check(int n) throws IOException {
            if (n <= 0) return;
            long total = read.addAndGet(n);
            if (total > maxBytes) {
                oversize.set(true);
                throw new OversizedBodyException(maxBytes);
            }
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) check(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) check(n);
            return n;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
