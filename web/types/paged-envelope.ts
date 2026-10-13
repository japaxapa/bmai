/**
 * The paged envelope api-design.md:7 promises for every collection —
 * `{items, page, size, total, totalPages}`. This is its one TypeScript home:
 * `types/` is where architecture.md §7 puts the DTO mirrors, and every list
 * (#6/#7/#8 after categories) binds to the same contract.
 */
export type PagedEnvelope<T> = {
  items: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
};
