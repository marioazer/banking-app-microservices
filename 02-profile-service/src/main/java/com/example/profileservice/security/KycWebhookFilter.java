package com.example.profileservice.security;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class KycWebhookFilter extends OncePerRequestFilter {

    // In a real app, this secret is provided by the vendor and securely injected
    @Value("${kyc.vendor.webhook.secret:SuperSecretVendorKey123!}")
    private String webhookSecret;

    private static final String WEBHOOK_PATH = "/api/v1/webhooks/kyc-update";
    private static final String SIGNATURE_HEADER = "X-Signature";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Only intercept the specific webhook path
        if (!request.getRequestURI().contains(WEBHOOK_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Wrap the request to cache the input stream
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);

        // 3-5. Extract the vendor's signature, compute our own, and compare them
        String signatureError = verifySignature(wrappedRequest);
        if (signatureError != null) {
            rejectRequest(response, signatureError);
            return;
        }

        // 6. Signature is valid! Pass the WRAPPED request to the Controller so it can read the body again
        filterChain.doFilter(wrappedRequest, response);
    }

    /**
     * Returns null if the vendor's signature is valid, or the specific rejection message otherwise.
     */
    private String verifySignature(CachedBodyHttpServletRequest wrappedRequest) {
        // 3. Extract the signature provided by the vendor
        String vendorSignature = wrappedRequest.getHeader(SIGNATURE_HEADER);
        if (vendorSignature == null) {
            return "Missing X-Signature header";
        }
        if (vendorSignature.isBlank()) {
            return "Missing X-Signature header";
        }

        // 4. Calculate our own HMAC signature based on the raw payload
        String body = new String(wrappedRequest.getCachedBody(), StandardCharsets.UTF_8);
        String calculatedSignature = calculateHmac(body, webhookSecret);

        // 5. Securely compare the signatures
        if (!isSignatureValid(vendorSignature, calculatedSignature)) {
            return "Invalid webhook signature";
        }
        return null;
    }

    private String calculateHmac(String data, String key) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC", e);
        }
    }

    /**
     * Prevents Timing Attacks by checking every single byte, even if a mismatch is found early.
     */
    private boolean isSignatureValid(String expected, String actual) {
        if (expected == null) return false;
        if (actual == null) return false;
        // MessageDigest.isEqual performs a cryptographic constant-time comparison
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private void rejectRequest(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }

    // =========================================================================================
    // Inner Class: Custom Request Wrapper to cache the InputStream
    // =========================================================================================
    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            InputStream requestInputStream = request.getInputStream();
            this.cachedBody = requestInputStream.readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedBodyServletInputStream(this.cachedBody);
        }

        @Override
        public BufferedReader getReader() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
            return new BufferedReader(new InputStreamReader(byteArrayInputStream));
        }

        public byte[] getCachedBody() {
            return this.cachedBody;
        }

        private static class CachedBodyServletInputStream extends ServletInputStream {
            private final InputStream cachedBodyInputStream;

            public CachedBodyServletInputStream(byte[] cachedBody) {
                this.cachedBodyInputStream = new ByteArrayInputStream(cachedBody);
            }

            @Override
            public boolean isFinished() {
                try { return cachedBodyInputStream.available() == 0; } 
                catch (IOException e) { return true; }
            }

            @Override
            public boolean isReady() { return true; }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int read() throws IOException {
                return cachedBodyInputStream.read();
            }
        }
    }
}