import { Suspense } from "react";
import { CategoriesPanel } from "./_components/categories-panel";

/**
 * Categories (ticket #5). The page stays thin (frontend-architecture §Structure):
 * everything here is interactive, so the whole surface is one client island
 * beside it. The island reads the page from the URL (useSearchParams), which
 * needs this Suspense boundary to prerender — the same shape the login page
 * uses for its `?next=`.
 */
export default function CategoriesPage() {
  return (
    <Suspense fallback={null}>
      <CategoriesPanel />
    </Suspense>
  );
}
