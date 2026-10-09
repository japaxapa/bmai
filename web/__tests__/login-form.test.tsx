import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { server } from "@/test/server";
import { push } from "@/test/next-navigation";
import { DEFAULT_API_BASE_URL } from "@/lib/api/base-url";
import { LoginForm } from "@/app/(auth)/login/_components/login-form";
import { Toaster } from "@/components/ui/sonner";
import { submitCredentials } from "@/test/helpers";

vi.mock("next/navigation", () => import("@/test/next-navigation"));

const LOGIN_URL = `${DEFAULT_API_BASE_URL}/api/auth/login`;

describe("login form", () => {
  beforeEach(() => {
    push.mockClear();
    window.history.replaceState({}, "", "/login");
  });

  test("posts the credentials and goes to / when no next is given", async () => {
    const requests: Request[] = [];
    server.use(
      http.post(LOGIN_URL, async ({ request }) => {
        requests.push(request);
        return HttpResponse.json({ ok: true });
      }),
    );
    const user = userEvent.setup();

    render(<LoginForm />);
    await submitCredentials(user);

    await waitFor(() => expect(push).toHaveBeenCalledWith("/"));
    expect(requests).toHaveLength(1);
    expect(requests[0].method).toBe("POST");
    expect(requests[0].credentials).toBe("include");
    expect(await requests[0].json()).toEqual({
      email: "admin@bms.local",
      password: "admin123",
    });
  });

  test("goes where the next query param points", async () => {
    window.history.replaceState({}, "", "/login?next=%2Ffinance");
    server.use(
      http.post(LOGIN_URL, () => HttpResponse.json({ ok: true })),
    );
    const user = userEvent.setup();

    render(<LoginForm />);
    await submitCredentials(user);

    await waitFor(() => expect(push).toHaveBeenCalledWith("/finance"));
  });

  test.each(["https://evil.com", "//evil.com", "/\\evil.com"])(
    "treats next=%s as absent instead of redirecting off-site",
    async (hostileNext) => {
      window.history.replaceState(
        {},
        "",
        `/login?next=${encodeURIComponent(hostileNext)}`,
      );
      server.use(
        http.post(LOGIN_URL, () => HttpResponse.json({ ok: true })),
      );
      const user = userEvent.setup();

      render(<LoginForm />);
      await submitCredentials(user);

      await waitFor(() => expect(push).toHaveBeenCalledWith("/"));
      expect(push).not.toHaveBeenCalledWith(hostileNext);
    },
  );

  test("refuses to post incomplete credentials and says why, per field", async () => {
    const requests: Request[] = [];
    server.use(
      http.post(LOGIN_URL, async ({ request }) => {
        requests.push(request);
        return HttpResponse.json({ ok: true });
      }),
    );
    const user = userEvent.setup();

    render(<LoginForm />);
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() =>
      expect(
        screen.getAllByRole("alert").map((node) => node.textContent),
      ).toEqual(["Enter a valid email address.", "Password is required."]),
    );
    expect(requests).toHaveLength(0);
    expect(push).not.toHaveBeenCalled();
  });

  test("toasts the API's detail and stays put when the login is refused", async () => {
    server.use(
      http.post(LOGIN_URL, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Unauthorized",
            status: 401,
            detail: "Invalid email or password",
          },
          { status: 401, headers: { "Content-Type": "application/problem+json" } },
        ),
      ),
    );
    const user = userEvent.setup();

    // The Toaster is mounted in the root layout, so tests that expect one
    // render it next to the form the same way.
    render(
      <>
        <LoginForm />
        <Toaster />
      </>,
    );
    await submitCredentials(user, {
      email: "admin@bms.local",
      password: "wrong-password",
    });

    // frontend-architecture §Forms: no fieldErrors on a 401 -> toast, not inline.
    expect(await screen.findByText("Invalid email or password")).toBeTruthy();
    expect(push).not.toHaveBeenCalled();
  });

  test("posts to whatever base URL the environment configures", async () => {
    // The dev box's 8080 is taken, so the API can live anywhere (issue #4 context).
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "http://localhost:9099");
    const postedToConfiguredBase: Request[] = [];
    const postedToDefaultBase: Request[] = [];
    server.use(
      http.post("http://localhost:9099/api/auth/login", async ({ request }) => {
        postedToConfiguredBase.push(request);
        return HttpResponse.json({ ok: true });
      }),
      http.post(LOGIN_URL, async ({ request }) => {
        postedToDefaultBase.push(request);
        return HttpResponse.json({ ok: true });
      }),
    );
    const user = userEvent.setup();

    render(<LoginForm />);
    await submitCredentials(user);

    await waitFor(() => expect(postedToConfiguredBase).toHaveLength(1));
    expect(postedToDefaultBase).toHaveLength(0);
    expect(push).toHaveBeenCalledWith("/");
  });
});
