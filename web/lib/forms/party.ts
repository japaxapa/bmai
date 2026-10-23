import { z } from "zod";
import { digitsOnly } from "@/lib/format/document";
import { optionalNumber } from "@/lib/forms/optional-number";

/**
 * The party's write shape, in one place: customer and supplier are the same
 * party (GLOSSARY "Parties"), so the field rules are one record — schema,
 * blank form, and form-strings → DTO — and neither panel can drift from the
 * other, nor create from edit. The panels own only the surface around them:
 * which endpoint, which controls, whose role sees them.
 *
 * <p>Shape courtesy only (frontend-architecture §Forms): zod gates the empty
 * boxes before they reach the API; react-hook-form owns registration, touched
 * state and where the messages land. No CPF/CNPJ judgement here — the length
 * and the check digits are the *domain's* rules (ADR 0017), answered as a
 * 422 whose sentence the form shows inline like any other refusal. The
 * whitespace-only name is deliberately legal for the same reason: the trim
 * (and therefore the "required") is the server's to apply.
 */
export const partySchema = z.object({
  name: z.string().min(1, "Name is required."),
  document: z.string().min(1, "Document is required."),
  email: z.string(),
  paymentTermsDays: optionalNumber("Payment terms"),
});

export type PartyValues = z.infer<typeof partySchema>;

/** Every box empty — so the server's own defaults (null, 30) are what apply. */
export const PARTY_EMPTY: PartyValues = {
  name: "",
  document: "",
  email: "",
  paymentTermsDays: "",
};

/**
 * What the write DTO takes (create and edit share the whole shape).
 * `email` and `paymentTermsDays` are optional: contact info a party may not
 * have yet, and terms the database is born with at 30 (business-rules §3) —
 * an empty box stays *out* of the body, so the server's own default is the
 * one that applies. The features' `SupplierBody` / `CustomerBody` are this
 * type under their own names.
 */
export type PartyBody = {
  name: string;
  document: string;
  email?: string | null;
  paymentTermsDays?: number;
};

/**
 * Form strings → the DTO: the document back to its canonical digits (the mask
 * is presentation — GLOSSARY "Canonical form"), a blank email and blank terms
 * left *out* so the server's own defaults (null, 30) are the ones that apply.
 */
export function partyToBody(values: PartyValues): PartyBody {
  const email = values.email.trim();
  return {
    name: values.name.trim(),
    document: digitsOnly(values.document),
    ...(email === "" ? {} : { email }),
    ...(values.paymentTermsDays.trim() === ""
      ? {}
      : { paymentTermsDays: Number(values.paymentTermsDays) }),
  };
}
