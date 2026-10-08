package com.example.bms.auth;

import com.example.bms.shared.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record LoginRequest(String email, String password) {
    }

    public record MeResponse(String email, String role) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        var token = authService.login(request.email(), request.password());
        if (token.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetails.unauthorized("Invalid email or password", httpRequest.getRequestURI()));
        }
        // ADR 0015: the token never appears in the body — httpOnly cookie only.
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, AuthCookie.forToken(token.get()).toString())
                .build();
    }

    /** Identity for the caller: read from the verified token, never from a DB hit. */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new MeResponse(jwt.getSubject(), jwt.getClaimAsString("role"));
    }

    /**
     * Stateless by design (ADR 0015): nothing is revoked server-side, the
     * browser is just asked to forget the token.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, AuthCookie.cleared().toString())
                .build();
    }
}
