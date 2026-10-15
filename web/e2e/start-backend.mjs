/**
 * The e2e backend's lifecycle (issue #45), run by Playwright's webServer:
 *
 *   1. `docker compose up -d --wait` — the compose Postgres, healthcheck and
 *      all; a no-op when it is already running.
 *   2. Recreate the `bms_e2e` database (with FORCE: drop connections from a
 *      previous, zombie run first). Every run gets freshly migrated schema
 *      and zero rows, and the dev `bms` database — the `db-data` volume a
 *      human keeps — is never touched.
 *   3. Boot the backend on `SERVER_PORT` (Playwright's webServer env sets it
 *      to E2E_API_PORT, 8082 — see support/api-base.ts; standalone runs fall
 *      back to the app's default 8081) pointed at `bms_e2e`, streaming its
 *      output into Playwright's webServer log. Tomcat binds only after
 *      context refresh (Flyway included), so a responding backend is a
 *      migrated one.
 *
 * Runs at the repo root regardless of the caller's cwd (Playwright starts
 * webServers from the config's directory, i.e. web/).
 */
import { spawn, spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");

/** Run a compose/psql helper synchronously, loudly, from the repo root. */
function run(command, args) {
  const result = spawnSync(command, args, { cwd: repoRoot, stdio: "inherit", shell: process.platform === "win32" });
  if (result.status !== 0) {
    console.error(`[e2e-backend] ${command} ${args.join(" ")} failed (${result.status})`);
    process.exit(result.status ?? 1);
  }
}

run("docker", ["compose", "up", "-d", "--wait"]);
// Separate calls: CREATE DATABASE cannot run in the implicit transaction a
// multi-statement `psql -c` would wrap both statements in.
run("docker", ["compose", "exec", "-T", "db", "dropdb", "--if-exists", "--force", "-U", "bms", "bms_e2e"]);
run("docker", ["compose", "exec", "-T", "db", "createdb", "-U", "bms", "bms_e2e"]);

const mvnw = process.platform === "win32" ? "mvnw.cmd" : "./mvnw";
const backend = spawn(mvnw, ["spring-boot:run"], {
  cwd: repoRoot,
  shell: process.platform === "win32",
  env: {
    ...process.env,
    DB_URL: "jdbc:postgresql://localhost:5432/bms_e2e",
    DB_USER: "bms",
    DB_PASSWORD: "bms",
  },
});

// Playwright stops a webServer by killing this script; pass the signal on so
// the JVM does not outlive the run and hold its port.
for (const signal of ["SIGTERM", "SIGINT"]) {
  process.on(signal, () => backend.kill(signal));
}
backend.on("exit", (code) => process.exit(code ?? 0));
