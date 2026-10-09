/**
 * The `?next=` a login redirect is allowed to follow.
 *
 * Anything that is not a same-site path — `https://evil.com`, the
 * protocol-relative `//evil.com`, the backslash form browsers normalize to
 * `//`, or no param at all — collapses to `/`, so the login page can never
 * become an open redirect.
 */
export function safeNext(next: string | null): string {
  if (
    next &&
    next.startsWith("/") &&
    !next.startsWith("//") &&
    !next.startsWith("/\\")
  ) {
    return next;
  }
  return "/";
}
