import { apiBaseUrl } from "@/lib/api/base-url";

/** What the API refuses with: RFC 9457, possibly carrying field errors. */
export type Problem = {
  status?: number;
  detail?: string;
  fieldErrors?: Record<string, string[]>;
};

async function refusalFrom(response: Response): Promise<Problem> {
  return (await response.json().catch(() => ({}))) as Problem;
}

/**
 * One write path for a JSON resource: POST or PUT the body to the API and
 * answer with the row the server stored — or *throw the RFC 9457 refusal
 * itself*, so every panel's onError lands on the same problem shape its
 * `handleRefusal` already routes (fieldErrors → the field, detail →
 * inline, anything else → a toast).
 *
 * The headers/credentials block lives here once: a mutation that forgets
 * `credentials: "include"` sends no session cookie (every write 401s), and
 * six copies of that block is six chances to find out the hard way.
 */
export async function writeJson<T>(
  path: string,
  method: "POST" | "PUT",
  body: unknown,
): Promise<T> {
  const response = await fetch(`${apiBaseUrl()}${path}`, {
    method,
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw await refusalFrom(response);
  }
  return (await response.json()) as T;
}
