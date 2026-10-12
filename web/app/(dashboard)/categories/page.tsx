import { CategoriesPanel } from "./_components/categories-panel";

/**
 * Categories (ticket #5). The page stays thin (frontend-architecture §Structure):
 * everything here is interactive, so the whole surface is one client island
 * beside it.
 */
export default function CategoriesPage() {
  return <CategoriesPanel />;
}
