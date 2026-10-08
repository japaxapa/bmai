package com.example.bms.auth;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * The login cookie (ADR 0015) — name, attributes and lifetime in one place,
 * because three things must agree: the cookie we set, the token's expiry, and
 * the cookie the filter chain later reads back as the credential.
 */
public final class AuthCookie {

    public static final String NAME = "access_token";

    /** ~12h (ADR 0015): the cookie and the JWT inside it end together. */
    public static final Duration LIFETIME = Duration.ofHours(12);

    private AuthCookie() {
    }

    public static ResponseCookie forToken(String token) {
        return attributes(token).maxAge(LIFETIME).build();
    }

    /** The same cookie, emptied — what a browser needs to forget it (RFC 6265). */
    public static ResponseCookie cleared() {
        return attributes("").maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder attributes(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/");
    }
}
