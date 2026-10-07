# Business Management System

A fictional Brazilian B2B wholesale distributor. Buys physical products from suppliers, resells to business customers on credit. Single location (v1). BRL. CPF/CNPJ-style identity documents. Code, docs, and UI in English.

## Parties

**Customer**:
A business that buys products on credit.
_Avoid_: Client, buyer, account

**Supplier**:
A business that sells products to us for resale.
_Avoid_: Vendor

**User**:
A person who logs in. Has a role and can be deactivated, never deleted.
_Avoid_: Account, employee-record

**Role**:
One of ADMIN (owner), MANAGER (sales supervisor), EMPLOYEE (sales clerk). Carried in JWT claims.
_Avoid_: Permission level, group

**Deactivated**:
A party, product, or user that can no longer be used in new documents but remains in history.
_Avoid_: Deleted, archived, disabled

## Catalog

**Product**:
A physical good bought and resold. Identified by SKU, has price, cost (weighted average), stock, minimum stock, category, supplier.
_Avoid_: Item, article

**SKU**:
The unique stock-keeping identifier of a product.
_Avoid_: Code, barcode

**Category**:
An ADMIN-managed grouping of products. Business data, not code. Name is unique.
_Avoid_: Type, tag

**Price**:
What the customer pays per unit. Locked onto the order item at CONFIRMED.
_Avoid_: Sale value

**Cost**:
The weighted-average price we paid per unit. Stored on the product, snapshot onto the order item at PROCESSING.
_Avoid_: Purchase price

**Minimum stock**:
The reorder signal for a product. Advisory only — never blocks a sale.
_Avoid_: Reorder point, safety stock

**Low stock**:
The dashboard state where stock is at or below minimum stock.
_Avoid_: Out of stock, understock

## Sales

**Sales order**:
A customer's request to buy products. Moves DRAFT → CONFIRMED → PROCESSING → COMPLETED. Terminal alternates: CANCELLED, RETURNED.
_Avoid_: Order (ambiguous), purchase (customer-side word for our sale)

**Order item**:
One product line on a sales order. Snapshots unit price at CONFIRMED and unit cost at PROCESSING.
_Avoid_: Line, order line

**Total**:
Sum of order-item subtotals. Computed by the backend, never trusted from the client.
_Avoid_: Amount, value

**DRAFT**:
An editable scratchpad. No stock effect, no money effect.
_Avoid_: New, pending

**CONFIRMED**:
An accepted order. Stock is reserved (not decremented). Cancellable with reservation release.
_Avoid_: Approved, validated

**PROCESSING**:
Goods are being picked. Reservation is released and a SALE movement decrements stock atomically.
_Avoid_: In progress, fulfilling

**COMPLETED**:
Goods handed over and invoice issued. Terminal for the happy path.
_Avoid_: Closed, delivered, done

**CANCELLED**:
A DRAFT or CONFIRMED order abandoned before stock moved. Zero ledger rows.
_Avoid_: Voided, deleted

**RETURNED**:
A COMPLETED order whose goods came back in full via the return operation.
_Avoid_: Cancelled-after-delivery, refunded

**Sales return**:
The post-COMPLETED operation that brings goods back (+RETURN movement) and reduces money owed (credit note).
_Avoid_: Cancel, refund (the operation)

## Inventory

**Inventory movement**:
One append-only row recording a real stock change. Signed quantity. Never updated or deleted.
_Avoid_: Stock change, transaction (ambiguous)

**On-hand**:
Physical stock. `SUM(movements)` by definition; mirrored in a `stock` column reconciled by test.
_Avoid_: Stock (ambiguous), quantity on hand

**Reservation**:
A hold on stock for a CONFIRMED order. Lives in `stock_reservations` until PROCESSING-entry or cancellation.
_Avoid_: Allocation, lock

**Available**:
Stock sellable right now. On-hand minus open reservations.
_Avoid_: Free stock

**Adjustment**:
A manual stock correction (ADJUSTMENT) or spoilage record (DAMAGE) with a required reason. ADMIN/MANAGER only.
_Avoid_: Fix, manual movement

**Stock value**:
On-hand quantity valued at current weighted-average cost. Used for inventory statistics.
_Avoid_: Inventory value

## Purchases

**Purchase order**:
Our order to a supplier. Moves DRAFT → ORDERED → RECEIVED → COMPLETED. CANCELLED from DRAFT/ORDERED.
_Avoid_: PO (in prose), supply order

**Receipt**:
The arrival of goods. Full receipt only (v1). Writes +PURCHASE movements, updates weighted-average cost, opens the payable.
_Avoid_: Goods receipt, delivery (ambiguous)

**Payable**:
What we owe a supplier. Born at RECEIVED. Paid partially or fully like a customer invoice in mirror.
_Avoid_: Supplier invoice, bill

## Money

**Invoice**:
The simplified payment request issued once at order COMPLETED. Sequential number, immutable. Has a due date (issue + customer terms).
_Avoid_: Bill, sales invoice

**Credit note**:
The record that reduces money owed after a return. Invoice total minus payments minus credit notes equals balance owed.
_Avoid_: Credit, refund note

**Refund**:
Cash paid back to a customer who overpaid after a return. An EXPENSE financial transaction.
_Avoid_: Reimbursement, payback

**Payment**:
Money in (customer) or out (supplier), partial allowed. Methods: PIX, bank transfer, boleto, cash.
_Avoid_: Settlement, installment (unless scheduled — out of scope)

**Payment terms**:
Per-customer / per-supplier net days, default 30. Due date is computed at invoice/payable issue.
_Avoid_: Credit period, terms

**Outstanding balance**:
Money owed right now. Invoice total minus payments minus credit notes.
_Avoid_: Open balance, debt

**Overdue**:
An unpaid balance past its due date. A visual flag only — no reminders or dunning in v1.
_Avoid_: Late, defaulted

**Financial transaction**:
One actual money movement. INCOME (customer payment) or EXPENSE (supplier payment, opex, refund). Status POSTED/VOID.
_Avoid_: Transaction (ambiguous), entry, ledger row

**Void**:
The only correction mechanism for money records. VOID the row and re-enter; never edit or delete.
_Avoid_: Cancel, reverse, delete

**Opex**:
Manual operating expenses (RENT, SALARIES, UTILITIES, MARKETING, FEES, OTHER). ADMIN-only.
_Avoid_: Costs (ambiguous), overhead

**Revenue (invoiced)**:
Money owed to us, counted when invoices issue (accrual-flavored headline metric).
_Avoid_: Revenue (unqualified), sales

**Cash collected**:
Money actually received (cash-flavored companion metric).
_Avoid_: Revenue, income

**Gross margin**:
Sum over sold items of (unit price − unit cost) × quantity.
_Avoid_: Profit (unqualified), markup

**COGS**:
Cost of goods sold. Implied by unit-cost snapshots; not a separate table.
_Avoid_: Cost of sales

**Weighted-average cost**:
The blended per-unit price paid, updated on every receipt. Returns re-enter stock at the current average (documented simplification vs FIFO).
_Avoid_: Average price

**Net profit**:
Gross margin minus opex.
_Avoid_: Profit (unqualified), bottom line
