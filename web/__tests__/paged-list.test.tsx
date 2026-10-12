import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { PagedList, type PagedEnvelope } from "@/components/shared/paged-list";
import { usePageParam } from "@/hooks/use-page-param";
import { replace } from "@/test/next-navigation";

// jsdom never mounts the app router, so anything calling useRouter() /
// useSearchParams() would blow up without this double.
vi.mock("next/navigation", () => import("@/test/next-navigation"));

type Row = { id: number; name: string };

function envelopeOf(
  page: number,
  names: string[],
  total = 6,
  size = 2,
): PagedEnvelope<Row> {
  return {
    items: names.map((name, index) => ({ id: page * size + index + 1, name })),
    page,
    size,
    total,
    totalPages: Math.ceil(total / size),
  };
}

/**
 * A stand-in for a real list (the categories panel is the first one):
 * page position from the URL, envelope looked up by that page, rows as
 * children — the wiring frontend-architecture and ADR 0019 prescribe.
 */
function RowList({ pages }: { pages: Record<number, PagedEnvelope<Row>> }) {
  const { page, goToPage } = usePageParam();
  return (
    <PagedList envelope={pages[page]} onPageChange={goToPage}>
      <ul>
        {pages[page]?.items.map((row) => (
          <li key={row.id}>{row.name}</li>
        ))}
      </ul>
    </PagedList>
  );
}

const PAGES = {
  0: envelopeOf(0, ["Mamões", "Nozes"]),
  1: envelopeOf(1, ["Peixes", "Tapioca"]),
  2: envelopeOf(2, ["Uvas"]),
};

describe("paged list", () => {
  beforeEach(() => {
    window.history.replaceState({}, "", "/categories");
  });

  test("summarises the page it was sent, Previous dead on the first page", async () => {
    render(<RowList pages={PAGES} />);

    expect(await screen.findByText("1–2 of 6")).toBeInTheDocument();
    expect(screen.getByText("Mamões")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Previous" }),
    ).toBeDisabled();
    expect(screen.getByRole("button", { name: "Next" })).toBeEnabled();
  });

  test("Next moves the page in the URL and swaps in that page's rows", async () => {
    render(<RowList pages={PAGES} />);
    await screen.findByText("Mamões");
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(await screen.findByText("Peixes")).toBeInTheDocument();
    // The rows of the page we left are gone — no mixing of pages.
    expect(screen.queryByText("Mamões")).toBeNull();
    expect(screen.getByText("3–4 of 6")).toBeInTheDocument();
    expect(replace).toHaveBeenCalledWith("/categories?page=1", {
      scroll: false,
    });
  });

  test("Previous drops back a page, and page 0 leaves the URL clean", async () => {
    window.history.replaceState({}, "", "/categories?page=1");
    render(<RowList pages={PAGES} />);
    await screen.findByText("Peixes");
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "Previous" }));

    expect(await screen.findByText("Mamões")).toBeInTheDocument();
    expect(replace).toHaveBeenCalledWith("/categories", { scroll: false });
  });

  test("hides the controls on a single-page list, summary still shown", async () => {
    render(
      <RowList
        pages={{ 0: envelopeOf(0, ["Mamões"], 1) }}
      />,
    );

    expect(await screen.findByText("1–1 of 1")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Next" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Previous" })).toBeNull();
  });

  test("says so when the list is empty", async () => {
    render(<RowList pages={{ 0: envelopeOf(0, [], 0) }} />);

    expect(await screen.findByText("Nothing here yet.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Next" })).toBeNull();
  });

  test("surfaces a failed fetch as text, not as a broken table", () => {
    render(
      <PagedList error={new Error("boom")} onPageChange={() => {}}>
        <ul>
          <li>Should not render</li>
        </ul>
      </PagedList>,
    );

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load this list.",
    );
    expect(screen.queryByText("Should not render")).toBeNull();
  });

  test("shows the loading state while the fetch is in flight", () => {
    render(
      <PagedList onPageChange={() => {}}>
        <ul>
          <li>Should not render yet</li>
        </ul>
      </PagedList>,
    );

    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });

  test("reads a garbage ?page= as the first page", async () => {
    window.history.replaceState({}, "", "/categories?page=abc");
    render(<RowList pages={PAGES} />);

    // No crash, no empty screen: the request falls back to page 0.
    expect(await screen.findByText("Mamões")).toBeInTheDocument();
  });
});
