import { z } from "zod";

/**
 * The optional-number *shape* rule every panel shares (frontend-architecture
 * §Forms): an empty box stays empty (the server owns the default), and a
 * filled box must survive `Number()`.
 *
 * "1e999" is a *valid* type=number value (the spec allows exponents) that
 * Number() reads as Infinity — JSON serializes that as `null`, and a null is
 * the server's born-default: the number the user typed would silently become
 * that default instead of being refused. Products' cost and minimum stock are
 * the origin of the rule, suppliers' payment terms the reason it had to be
 * shared — on edit a null means *keep the stored terms*, which is worse than
 * a silent zero: it is a silent non-change.
 *
 * No caps or ranges here — the server owns those (price > 0, cost ≥ 0, …),
 * and its messages are the ones the fields must show.
 */
export const optionalNumber = (label: string) =>
  z
    .string()
    .refine((value) => value.trim() === "" || Number.isFinite(Number(value)), {
      message: `${label} must be a number.`,
    });
