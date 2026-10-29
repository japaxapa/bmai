/**
 * Money is BRL everywhere (requirements §4: "BRL formatting"), displayed with
 * the reader's own separators. One formatter for every money cell, so a list
 * and a detail can never disagree about how 37.3 reads.
 */
const brl = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

export function formatBRL(value: number): string {
  return brl.format(value);
}
