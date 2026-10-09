import type { ReactNode } from "react";
import { Shell } from "./_components/shell";

/** Sidebar + header shell for the signed-in app (frontend-architecture §Structure). */
export default function DashboardLayout({ children }: { children: ReactNode }) {
  return <Shell>{children}</Shell>;
}
