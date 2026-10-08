package com.example.bms.shared;

import com.example.bms.auth.AuthCookie;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import tools.jackson.databind.ObjectMapper;

/**
 * The base URL layer (architecture §5): everything authenticated except the
 * endpoints that must answer without a token.
 *
 * CSRF is disabled in both chains, with the ADR 0015 rationale: the API is
 * JSON-only and the JWT travels in a SameSite=Lax cookie, so a cross-site form
 * post cannot deliver a body the server would act on; with no session there is
 * also nothing for a token to ride on.
 *
 * Two chains, because a single one would let the resource server reject a bad
 * cookie (401) before authorization is even considered — which would make
 * "logout with an expired token" unanswerable.
 *
 * {@code @EnableMethodSecurity} is the other half of enforcement: the filter
 * chain answers "who is calling", this one answers "what may they call".
 * Without it every later {@code @PreAuthorize} compiles and is silently inert,
 * while business-rules §4 (Permissions) and requirements §4 "Backend is the
 * security boundary" both promise the rule is enforced.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Endpoints that answer without a credential, valid or not. */
    @Bean
    @Order(1)
    SecurityFilterChain publicEndpoints(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/auth/login", "/api/auth/logout",
                        "/actuator/health", "/error")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /** Everything else: a cookie credential or no answer (architecture §5). */
    @Bean
    @Order(2)
    SecurityFilterChain authenticatedApi(HttpSecurity http,
                                         ProblemDetailAuthenticationEntryPoint entryPoint) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        // ADR 0015: the credential is the login cookie, not an
                        // Authorization header, so the bearer resolver reads that
                        // cookie name (one definition, in AuthCookie).
                        .bearerTokenResolver(cookieBearerTokenResolver())
                        // Without this the resource server answers its own bare 401;
                        // bad tokens must carry the same problem+json envelope.
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> {
                        }))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    private BearerTokenResolver cookieBearerTokenResolver() {
        return request -> {
            var cookies = request.getCookies();
            if (cookies == null) {
                return null;
            }
            return Arrays.stream(cookies)
                    .filter(cookie -> AuthCookie.NAME.equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        };
    }

    @Bean
    ProblemDetailAuthenticationEntryPoint problemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new ProblemDetailAuthenticationEntryPoint(objectMapper);
    }

    /**
     * ADR 0015: "CORS allows exactly {@code http://localhost:3000} with
     * credentials". The Next.js dev server on :3000 calls this API
     * cross-origin with {@code credentials: include}, so the login cookie can
     * only travel under one explicit origin — {@code *} is not even a legal
     * answer here, since browsers refuse {@code allow-credentials} with a
     * wildcard.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // JSON bodies preflight: "Content-Type" is not a safelisted request
        // header, so it must be named explicitly.
        configuration.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE));
        configuration.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${auth.jwt.secret}") String secret) {
        // ADR 0015: HS256 over a shared secret.
        return new NimbusJwtEncoder(new ImmutableSecret<>(hmacKey(secret)));
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${auth.jwt.secret}") String secret) {
        return NimbusJwtDecoder.withSecretKey(hmacKey(secret)).build();
    }

    /**
     * One construction for both directions, fed by the same property, so the
     * encoder and decoder cannot drift apart (ADR 0015: HS256).
     */
    private SecretKeySpec hmacKey(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Architecture §4: BCrypt(10).
        return new BCryptPasswordEncoder(10);
    }
}
