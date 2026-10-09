import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

export type User = ReturnType<typeof userEvent.setup>;

/** The seeded admin (README "Demo the auth"), the default across suites. */
export const SEEDED_ADMIN = {
  email: "admin@bms.local",
  password: "admin123",
} as const;

/**
 * The login journey: type both credentials and submit. Shared so the
 * field labels and button name live in one place — if the form's copy
 * changes, there is exactly one edit.
 */
export async function submitCredentials(
  user: User,
  credentials: { email: string; password: string } = SEEDED_ADMIN,
) {
  await user.type(await screen.findByLabelText("Email"), credentials.email);
  await user.type(screen.getByLabelText("Password"), credentials.password);
  await user.click(screen.getByRole("button", { name: "Sign in" }));
}
