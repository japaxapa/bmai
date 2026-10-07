# Money records are append-only (VOID + re-enter)

No money row — invoice, payable, payment, credit note, expense, transaction — is ever edited or deleted through the API. Corrections void the row (`status = VOID`) and enter a new one; balances exclude VOID rows. This turns the "supervisor would have to enter the DB to fake a trail" rationale into an enforced API property.
