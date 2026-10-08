import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import LoginPage from "@/app/(auth)/login/page";
import { AppProviders } from "@/providers";

describe("login page", () => {
  test("shows the sign-in heading and prompt inside the app providers", () => {
    render(
      <AppProviders>
        <LoginPage />
      </AppProviders>,
    );

    expect(
      screen.getByRole("heading", { level: 1, name: "Sign in" }),
    ).toBeDefined();
    expect(
      screen.getByText("Sign in with your email and password."),
    ).toBeDefined();
  });
});
