# Keep order status orthogonal to payment status

Orders never carry PAID/unpaid states. Fulfillment (DRAFT…COMPLETED/RETURNED) and money (invoice − payments − credit notes) are separate axes. The alternative — payment as an order status — cannot represent "delivered but half unpaid" without fake states, and it would tangle the state machine with the cash ledger.
