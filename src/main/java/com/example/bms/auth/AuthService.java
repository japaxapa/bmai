package com.example.bms.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Credential checks for login (architecture §4: BCrypt, roles in claims).
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
    }

    /**
     * The JWT for a correct email/password pair, empty when the credentials are
     * rejected — unknown email and wrong password are deliberately the same
     * answer, so a caller cannot use this to enumerate users.
     */
    public Optional<String> login(String email, String password) {
        return users.findByEmail(email)
                .filter(user -> passwordEncoder.matches(password, user.passwordHash()))
                .map(this::issueToken);
    }

    /** ADR 0015: claims {@code sub}, {@code role}, {@code exp}. */
    private String issueToken(User user) {
        var issuedAt = Instant.now();
        var claims = JwtClaimsSet.builder()
                .subject(user.email())
                .claim("role", user.role().name())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(AuthCookie.LIFETIME))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
