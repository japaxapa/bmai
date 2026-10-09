/**
 * The HS256 key the proxy checks login cookies against (ADR 0015).
 *
 * `AUTH_JWT_SECRET` wins when set — any shared environment must set it for
 * both the backend and the web app, or cookies signed by one side are
 * rejected by the other (a silent login loop). With no env set, the committed
 * dev default keeps a fresh clone working with zero configuration, mirroring
 * `auth.jwt.secret` in `src/main/resources/application.properties`; a parity
 * test on the backend side (`SharedJwtSecretParityTest`, ./mvnw test) fails
 * if the two ever drift.
 */
export const DEV_JWT_SECRET =
  "3eced932e8977c5e1cbed61de5858d158506ca8e36f9483f5bf3c6ded4d0b92e";

export function jwtSecret(): string {
  return process.env.AUTH_JWT_SECRET || DEV_JWT_SECRET;
}
