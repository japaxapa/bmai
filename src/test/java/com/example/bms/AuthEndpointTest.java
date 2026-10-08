package com.example.bms;

import com.example.bms.auth.AuthService;
import com.example.bms.auth.Role;
import com.example.bms.auth.User;
import com.example.bms.auth.UserRepository;
import com.example.bms.shared.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seam A (agreed with the user): the auth HTTP surface, observed through
 * MockMvc exactly as curl would see it — status, content type and body shape
 * only. The database boundary is stubbed here; the real seeded user is
 * exercised end to end by the full-stack Seam B test.
 *
 * {@code @WebMvcTest} scans controllers only, so the beans the login path
 * needs are imported explicitly.
 */
@WebMvcTest
@Import({SecurityConfig.class, AuthService.class})
class AuthEndpointTest {

    @Autowired
    private MockMvc mvc;

    /**
     * The stubbed database boundary: one known user, everyone else unknown.
     * BCrypt(10) of "correct-password", so a posted password can only pass by
     * actually matching a hash.
     */
    @TestConfiguration
    static class StubbedUsers {

        static final String CLERK_HASH =
                "$2a$10$sChJDgu0OUQbt7u3K626o.XEY0pYtdS5qIWXcJDwhLT7LhetiVJUu";

        @Bean
        UserRepository userRepository() {
            return email -> "clerk@bms.local".equals(email)
                    ? Optional.of(new User("clerk@bms.local", CLERK_HASH, Role.EMPLOYEE))
                    : Optional.empty();
        }
    }

    /**
     * The ways a login can be wrong. All must answer identically: a
     * caller that can tell "no such user" from "wrong password" can enumerate
     * users, and a missing field on either side must not become a 500.
     */
    static Stream<String> badCredentials() {
        return Stream.of(
                "{\"email\":null,\"password\":\"wrong-password\"}",
                "{\"email\":\"nobody@bms.local\",\"password\":\"wrong-password\"}",
                "{\"email\":\"clerk@bms.local\",\"password\":\"wrong-password\"}",
                "{\"email\":\"clerk@bms.local\"}");
    }

    @Test
    void meWithoutCredentialsIsRejectedWithProblemJson() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @ParameterizedTest
    @MethodSource("badCredentials")
    void badCredentialsAreRejectedWithTheSameProblemBody(String body) throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    /**
     * ADR 0015: the JWT travels in an httpOnly SameSite=Lax cookie for ~12h,
     * never in a body (XSS-readable) — so the cookie is the whole contract
     * this endpoint has with the browser.
     */
    @Test
    void loginWithCorrectCredentialsSetsHttpOnlyJwtCookie() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"clerk@bms.local\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", allOf(
                        matchesPattern("access_token=[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+;.*"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Lax"),
                        containsString("Path=/"),
                        containsString("Max-Age=43200"))));
    }

    /**
     * AC: {@code /auth/me} answers 401 "otherwise" — not only for a missing
     * cookie but for one we would not have issued. Same envelope either way, so
     * a client's 401 handling has exactly one path.
     */
    @Test
    void meWithTamperedCookieIsRejectedWithProblemJson() throws Exception {
        mvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", "not.a.token")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid or expired token"));
    }

    /**
     * Logout is only a cookie clear: ADR 0015 has no token store, so the JWT
     * itself stays valid until it expires. That is the trade the ADR accepts
     * (statelessness over revocation); the browser stops sending it, and the
     * next login overwrites it.
     */
    @Test
    void logoutClearsTheLoginCookie() throws Exception {
        var login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"clerk@bms.local\",\"password\":\"correct-password\"}"))
                .andReturn();
        var cookie = login.getResponse().getCookie("access_token");
        assertThat(cookie).as("login must set the cookie this test replays").isNotNull();

        mvc.perform(post("/api/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", allOf(
                        startsWith("access_token="),
                        containsString("Max-Age=0"))));
    }

    /**
     * The session you most need to end is the one whose token has gone bad —
     * so logout must answer without a credential at all.
     */
    @Test
    void logoutClearsTheCookieEvenWhenTheTokenIsNoLongerValid() throws Exception {
        mvc.perform(post("/api/auth/logout").cookie(new Cookie("access_token", "not.a.token")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", allOf(
                        startsWith("access_token="),
                        containsString("Max-Age=0"))));
    }

    /**
     * The round trip the browser will make: log in, then present the cookie
     * back. Proves the issued token is the same one the filter chain accepts,
     * and that the role claim survives it (ADR 0015: authorization is a claim
     * check, not a per-request DB hit).
     */
    @Test
    void meWithValidCookieReturnsTheUsersRole() throws Exception {
        var login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"clerk@bms.local\",\"password\":\"correct-password\"}"))
                .andReturn();
        var cookie = login.getResponse().getCookie("access_token");
        assertThat(cookie).as("login must set the cookie this test replays").isNotNull();

        mvc.perform(get("/api/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.email").value("clerk@bms.local"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    /**
     * ADR 0015: "CORS allows exactly {@code http://localhost:3000} with
     * credentials". A login from the Next.js dev server preflights (its JSON
     * body is not safelisted), and only that origin may be told it can send
     * the credentialed request — a wildcard origin is refused by browsers the
     * moment credentials are allowed, so it must never be the answer.
     */
    @Test
    void preflightFromTheFrontendOriginIsAllowedWithCredentials() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void preflightFromAnyOtherOriginIsRefused() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
