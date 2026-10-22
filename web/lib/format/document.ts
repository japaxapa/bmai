/**
 * The CPF/CNPJ document's two spellings (GLOSSARY "Canonical form"): what a
 * person *sees* and what the API *stores*.
 *
 * The punctuation is presentation and lives here, frontend-only (ADR 0017
 * keeps the domain's own judgement — length and check digits — server-side):
 * `formatDocument` spells the digits for the eye, `digitsOnly` strips them
 * back before anything crosses the wire, so the unique index, the search and
 * the 409's sentence only ever meet one spelling of a document.
 */

/** The digits in a value, whatever punctuation it arrived with. */
export function digitsOnly(value: string): string {
  return value.replace(/\D/g, "");
}

/** A CPF is 11 digits; anything longer is a CNPJ (14). */
const CPF_LENGTH = 11;

/** `#` is a digit's slot, anything else a literal the mask inserts. */
const CPF_PATTERN = ["#", "#", "#", ".", "#", "#", "#", ".", "#", "#", "#", "-", "#", "#"];
// `##.###.###/####-##`: the CNPJ's own four groups, not the CPF's three.
const CNPJ_PATTERN = ["#", "#", ".", "#", "#", "#", ".", "#", "#", "#", "/", "#", "#", "#", "#", "-", "#", "#"];

/**
 * The document spelled for reading — progressively: up to 11 digits it is a
 * CPF (`123.456.789-09`), and the 12th digit re-spells the whole string as a
 * CNPJ (`11.222.333/0001-81`). A separator only ever appears once a digit
 * follows it, so half-typed documents read as half-typed documents.
 *
 * Never truncates the digits: past 14 they are not a document, and the
 * *domain* is the one that says so — its 422 reaches the reader inline,
 * which a silently swallowed digit could not.
 */
export function formatDocument(value: string): string {
  const digits = digitsOnly(value);
  const pattern = digits.length <= CPF_LENGTH ? CPF_PATTERN : CNPJ_PATTERN;
  let out = "";
  let index = 0;
  for (const token of pattern) {
    if (index >= digits.length) break;
    if (token === "#") {
      out += digits[index];
      index += 1;
    } else {
      out += token;
    }
  }
  return out + digits.slice(index);
}
