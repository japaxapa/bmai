# Stateless JWT in httpOnly cookie

Auth is a signed JWT (claims `sub`, `role`, `exp`, ~12h) stored in an httpOnly `SameSite=Lax` cookie, not localStorage (XSS-readable) and not server sessions (stateful). Logout clears the cookie; no refresh-token rotation (a token store is state). CORS allows exactly `http://localhost:3000` with credentials. Roles in claims make authorization a claim check, not a per-request DB hit.

The HS256 key is read from `auth.jwt.secret` (`AUTH_JWT_SECRET`), which ships with a committed dev default so a fresh clone boots and logs in with zero configuration; any shared environment must override it.
