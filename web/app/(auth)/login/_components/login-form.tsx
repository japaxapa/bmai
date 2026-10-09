"use client";

import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { useRouter, useSearchParams } from "next/navigation";
import { apiBaseUrl } from "@/lib/api/base-url";
import { safeNext } from "@/lib/safe-next";
import { useAuth } from "@/providers/auth-provider";

// Shape courtesy only (frontend-architecture §Forms): zod gates the two
// fields before they reach the API; react-hook-form owns registration,
// touched state and where the messages land.
const loginSchema = z.object({
  email: z.email("Enter a valid email address."),
  password: z.string().min(1, "Password is required."),
});

type LoginValues = z.infer<typeof loginSchema>;

export function LoginForm() {
  const router = useRouter();
  const next = useSearchParams().get("next");
  const { refresh } = useAuth();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<LoginValues>();

  const submit = handleSubmit(async (values) => {
    const parsed = loginSchema.safeParse(values);
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        setError(issue.path[0] as "email" | "password", {
          message: issue.message,
        });
      }
      return;
    }

    const response = await fetch(`${apiBaseUrl()}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify(parsed.data),
    });

    if (response.ok) {
      // The cookie is set but the shell's `/auth/me` read predates it —
      // re-read before navigating so `/` renders the role, not the old state.
      await refresh();
      router.push(safeNext(next));
      return;
    }

    // RFC 9457: the refusal carries its own sentence in `detail`. A login 401
    // has no `fieldErrors`, so per frontend-architecture §Forms it lands as a
    // toast — visible, dismissible, and out of the field layout — while the
    // page stays exactly where it is.
    const problem = (await response.json().catch(() => ({}))) as {
      detail?: unknown;
    };
    toast.error(
      typeof problem.detail === "string" ? problem.detail : "Sign-in failed.",
    );
  });

  const fieldError = (message?: string) =>
    message ? (
      <p role="alert" className="text-sm text-red-600">
        {message}
      </p>
    ) : null;

  return (
    <form onSubmit={submit} noValidate className="mt-6 flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <label htmlFor="email" className="text-sm font-medium">
          Email
        </label>
        <input
          id="email"
          type="email"
          autoComplete="email"
          required
          aria-invalid={Boolean(errors.email)}
          className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
          {...register("email")}
        />
        {fieldError(errors.email?.message)}
      </div>
      <div className="flex flex-col gap-1.5">
        <label htmlFor="password" className="text-sm font-medium">
          Password
        </label>
        <input
          id="password"
          type="password"
          autoComplete="current-password"
          required
          aria-invalid={Boolean(errors.password)}
          className="h-9 rounded-md border border-neutral-300 px-3 text-sm outline-none focus:border-neutral-500 dark:border-neutral-700"
          {...register("password")}
        />
        {fieldError(errors.password?.message)}
      </div>
      <button
        type="submit"
        className="h-9 rounded-md bg-neutral-900 text-sm font-medium text-white hover:bg-neutral-700 dark:bg-neutral-100 dark:text-neutral-900"
      >
        Sign in
      </button>
    </form>
  );
}
