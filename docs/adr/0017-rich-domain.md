# Rich domain objects own their invariants

State-machine guards, availability checks, and the stock floor live on entities (`order.confirm()`, `product.applyMovement()`), so no caller can bypass them by skipping a service — the domain refuses bad transitions and the advice layer maps that refusal to 409/422. The anemic alternative (getters/setters + all logic in services) makes every bypass a bug. Accepted friction: JPA fights rich models slightly (proxies, no-arg constructors, equals/hashCode) — documented where felt.
