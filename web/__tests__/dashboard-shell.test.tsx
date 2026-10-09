import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { server } from "@/test/server";
import { push } from "@/test/next-navigation";
import { DEFAULT_API_BASE_URL } from "@/lib/api/base-url";
import { AppProviders } from "@/providers";
import { LoginForm } from "@/app/(auth)/login/_components/login-form";
import DashboardLayout from "@/app/(dashboard)/layout";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const ME_URL = `${DEFAULT_API_BASE_URL}/api/auth/me`;
const LOGIN_URL = `${DEFAULT_API_BASE_URL}/api/auth/login`;

function renderShell() {
  return render(
    <AppProviders>
      <DashboardLayout>
        <p>page content</p>
      </DashboardLayout>
    </AppProviders>,
  );
}

function renderShellWithLoginForm() {
  return render(
    <AppProviders>
      <DashboardLayout>
        <p>page content</p>
      </DashboardLayout>
      <LoginForm />
    </AppProviders>,
  );
}

describe("dashboard shell", () => {
  beforeEach(() => {
    push.mockClear();
  });

  test("header shows the signed-in user's email and role from /auth/me", async () => {
    server.use(
      http.get(ME_URL, () =>
        HttpResponse.json({ email: "admin@bms.local", role: "ADMIN" }),
      ),
    );

    renderShell();

    expect(await screen.findByText("admin@bms.local")).toBeInTheDocument();
    expect(screen.getByText("ADMIN")).toBeInTheDocument();
    expect(screen.getByText("page content")).toBeInTheDocument();
  });

  test("401 from /auth/me shows a not-signed-in shell with no stale user", async () => {
    server.use(
      http.get(ME_URL, () =>
        HttpResponse.json(null, { status: 401 }),
      ),
    );

    renderShell();

    expect(await screen.findByText("Not signed in")).toBeInTheDocument();
    expect(screen.queryByText("admin@bms.local")).toBeNull();
  });

  test("an EMPLOYEE does not see the ADMIN-only Users nav item", async () => {
    server.use(
      http.get(ME_URL, () =>
        HttpResponse.json({ email: "clerk@bms.local", role: "EMPLOYEE" }),
      ),
    );

    renderShell();

    expect(await screen.findByText("clerk@bms.local")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Users" })).toBeNull();
    expect(screen.getByRole("link", { name: "Dashboard" })).toBeInTheDocument();
  });

  test("a successful login makes the shell read the fresh /auth/me", async () => {
    let meCalls = 0;
    server.use(
      http.get(ME_URL, () => {
        meCalls += 1;
        return meCalls === 1
          ? HttpResponse.json(null, { status: 401 })
          : HttpResponse.json({ email: "admin@bms.local", role: "ADMIN" });
      }),
      http.post(LOGIN_URL, () => HttpResponse.json({})),
    );
    const user = userEvent.setup();

    renderShellWithLoginForm();
    // The first /auth/me has settled: the shell really is signed-out.
    expect(await screen.findByText("Not signed in")).toBeInTheDocument();

    await user.type(await screen.findByLabelText("Email"), "admin@bms.local");
    await user.type(screen.getByLabelText("Password"), "admin123");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByText("admin@bms.local")).toBeInTheDocument();
    expect(push).toHaveBeenCalledWith("/");
    expect(meCalls).toBe(2);
  });
});
