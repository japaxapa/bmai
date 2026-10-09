package com.example.bms.shared;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0015: the login cookie is HS256-signed over one secret shared by the
 * backend (this side, which mints tokens) and the Next.js proxy (which
 * verifies them). With no env set, both fall back to a committed dev default
 * so a fresh clone works with zero configuration — two literals that must
 * never drift, or every cookie is silently rejected and the browser loops
 * through /login.
 *
 * The invariant used to be asserted from the web suite by reading this
 * side's {@code application.properties} through a regex — which made the
 * frontend's tests fail for backend-only config edits, the wrong direction
 * for a monorepo boundary. The check lives here instead: the root build
 * already owns both trees, and this suite is where config/security tests
 * belong. A failed assertion means one of the two committed defaults was
 * edited without the other — change both, or set AUTH_JWT_SECRET properly.
 */
class SharedJwtSecretParityTest {

    private static final Pattern PROPERTIES_DEFAULT =
            Pattern.compile("^auth\\.jwt\\.secret=\\$\\{AUTH_JWT_SECRET:([^}]+)\\}$", Pattern.MULTILINE);
    private static final Pattern WEB_DEFAULT =
            Pattern.compile("DEV_JWT_SECRET\\s*=\\s*\"([^\"]+)\"");

    @Test
    void theCommittedDevDefaultMatchesTheWebProxys() throws IOException {
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));
        String webSecretSource = Files.readString(Path.of("web/lib/auth/jwt-secret.ts"));

        String backendDefault = extract(PROPERTIES_DEFAULT, properties,
                "auth.jwt.secret=<AUTH_JWT_SECRET:default> in application.properties");
        String webDefault = extract(WEB_DEFAULT, webSecretSource,
                "DEV_JWT_SECRET = \"...\" in web/lib/auth/jwt-secret.ts");

        assertThat(webDefault)
                .as("the web proxy's committed HS256 default must equal the backend's, or logged-in "
                        + "browsers loop through /login (ADR 0015)")
                .isEqualTo(backendDefault);
    }

    private static String extract(Pattern pattern, String source, String what) {
        Matcher matcher = pattern.matcher(source);
        assertThat(matcher.find())
                .as("expected to find " + what)
                .isTrue();
        return matcher.group(1);
    }
}
