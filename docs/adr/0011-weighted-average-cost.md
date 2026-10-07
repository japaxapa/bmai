# Weighted-average cost with per-item snapshots

`product.cost` is the weighted average updated on every receipt (`(stock × avg + qty × cost)/(stock + qty)`; empty stock → avg = cost). Each OrderItem snapshots `unitCost` at PROCESSING alongside the `unitPrice` locked at CONFIRMED, so later price changes never rewrite history. Gross margin = Σ(unitPrice − unitCost) × qty. Sales returns re-enter at the current average — a documented simplification; true cost layers (FIFO) are cut-listed.
