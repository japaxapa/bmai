# Business Management System — Project Initialization

I want to start a portfolio project for a **Business Management System** using:

* **Backend:** Java + Spring Boot
* **Frontend:** Next.js + TypeScript + shadcn/ui
* **Database:** PostgreSQL
* **API:** REST
* **Environment:** Local development
* **Deployment:** Not required initially
* **Purpose:** Professional portfolio project aimed at demonstrating junior-to-strong-junior software engineering skills

I want you to use the **grill-me-with-docs** skill to guide me through designing and building this project.

Your role should be that of a **Senior Software Engineer / Software Architect / Technical Lead** mentoring a junior developer.

Do not simply generate the entire project for me.

Instead, **grill me through the decisions**, identify gaps, challenge assumptions, explain tradeoffs when necessary, and help me produce professional project documentation before implementation.

---

# 1. Project Concept

The application is a small-to-medium business management system.

The main goal is to manage business operations through interconnected modules rather than creating isolated CRUD functionality.

The core business flow should be:

```text
Customer
   ↓
Sales Order
   ↓
Order Items
   ↓
Products
   ↓
Inventory
   ↓
Payment / Financial Transaction
   ↓
Dashboard / Reports
```

The system should eventually support:

* Dashboard
* Customers
* Products
* Suppliers
* Sales Orders
* Inventory
* Purchase Orders
* Finance
* Users and Roles
* Reports

The system should model realistic business rules and relationships.

---

# 2. Main Business Modules

## Dashboard

The dashboard should provide business-level information such as:

* Revenue
* Expenses
* Profit
* Recent orders
* Low-stock products
* Outstanding payments
* Revenue vs expenses
* Sales statistics
* Inventory statistics

The dashboard should consume aggregated backend data rather than downloading all records and calculating everything in the frontend.

---

## Customers

Customers should support:

* Create
* Edit
* View
* Deactivate
* Search
* Filtering
* Pagination
* Order history
* Payment history

Potential fields:

```text
id
name
email
phone
document
address
status
createdAt
updatedAt
```

---

## Products

Products/services should support:

* Create
* Edit
* View
* Deactivate
* Search
* Filtering
* Pagination
* Stock information
* Supplier association
* Category association

Potential fields:

```text
id
sku
name
description
type
price
cost
stock
minimumStock
categoryId
supplierId
status
createdAt
updatedAt
```

Important business rules may include:

* Price must be greater than zero
* Cost cannot be negative
* SKU must be unique
* Stock cannot become negative
* Minimum stock cannot be negative

---

## Sales Orders

Orders are one of the core modules.

Potential entities:

```text
Order
    id
    customerId
    status
    total
    createdAt
    updatedAt

OrderItem
    id
    orderId
    productId
    quantity
    unitPrice
    subtotal
```

Possible order lifecycle:

```text
DRAFT
   ↓
CONFIRMED
   ↓
PROCESSING
   ↓
COMPLETED
```

Possible cancellation:

```text
DRAFT ──────────→ CANCELLED
CONFIRMED ───────→ CANCELLED
```

The system should not allow arbitrary status changes.

I want proper business rules around order transitions.

---

## Inventory

Inventory should use an **inventory movement model**, rather than simply incrementing and decrementing a stock field.

Potential entity:

```text
InventoryMovement
    id
    productId
    type
    quantity
    reason
    referenceId
    createdAt
```

Possible movement types:

```text
PURCHASE
SALE
ADJUSTMENT
RETURN
DAMAGE
```

Example:

```text
+100 PURCHASE
-2 SALE
-1 SALE
+5 PURCHASE
-1 DAMAGE

Current stock = 101
```

The movement history should provide an audit trail.

---

## Suppliers

Suppliers should support:

* Create
* Edit
* View
* Deactivate
* Search
* Product relationships
* Purchase history

Potential fields:

```text
id
name
email
phone
document
address
status
```

---

## Purchase Orders

Purchases should allow the business to replenish inventory.

Potential entities:

```text
PurchaseOrder
    id
    supplierId
    status
    total
    createdAt

PurchaseItem
    purchaseId
    productId
    quantity
    unitCost
    subtotal
```

Possible lifecycle:

```text
DRAFT
   ↓
ORDERED
   ↓
RECEIVED
   ↓
COMPLETED
```

When a purchase is received:

```text
Purchase received
       ↓
Inventory movement created
       ↓
Product stock increases
```

This operation should be transactional.

---

## Finance

The financial module should be intentionally simplified.

It does not need to become a complete accounting system.

It should support concepts such as:

* Income
* Expenses
* Payments
* Accounts receivable
* Accounts payable
* Financial transaction history

Potential entity:

```text
FinancialTransaction
    id
    type
    category
    amount
    description
    referenceId
    status
    transactionDate
```

Types:

```text
INCOME
EXPENSE
```

---

## Authentication and Authorization

The application should have users and roles.

Potential roles:

```text
ADMIN
MANAGER
EMPLOYEE
```

Example permissions:

| Feature   | Admin | Manager | Employee |
| --------- | ----: | ------: | -------: |
| Dashboard |     ✓ |       ✓ |        ✓ |
| Customers |     ✓ |       ✓ |        ✓ |
| Products  |     ✓ |       ✓ |        ✓ |
| Inventory |     ✓ |       ✓ |        ✓ |
| Orders    |     ✓ |       ✓ |        ✓ |
| Finance   |     ✓ |       ✓ |        — |
| Users     |     ✓ |       — |        — |

Authorization must be enforced by the backend.

The frontend should not be treated as the security boundary.

---

# 3. Important Business Transaction

One of the project's showcase operations should be confirming an order.

For example:

```text
POST /api/orders/{id}/confirm
```

The backend should conceptually:

1. Load the order
2. Validate the order state
3. Validate the customer
4. Validate the products
5. Check inventory
6. Update/reserve stock
7. Create inventory movements
8. Create the appropriate financial record
9. Change the order status
10. Commit the transaction

If one of the important operations fails, the transaction should roll back appropriately.

I want this flow to become one of the project's main examples of transactional business logic.

---

# 4. Proposed Architecture

Backend:

```text
Controller
    ↓
Application / Service
    ↓
Business Rules
    ↓
Repository
    ↓
PostgreSQL
```

Frontend:

```text
Next.js App Router
       ↓
Pages / Route Segments
       ↓
Feature Components
       ↓
API Services
       ↓
Spring Boot REST API
```

I prefer keeping Next.js pages small and using:

```text
app/
features/
components/
services/
hooks/
providers/
lib/
types/
```

I prefer feature-oriented organization where appropriate.

For example:

```text
customers/
├── page.tsx
└── _components/
    ├── CustomerList.tsx
    ├── CustomerFilters.tsx
    ├── CustomerDialog.tsx
    └── CustomerTable.tsx
```

For Java, I am considering feature-oriented packages such as:

```text
auth/
customer/
product/
order/
inventory/
supplier/
purchase/
finance/
```

with each feature potentially containing:

```text
controller/
service/
repository/
entity/
dto/
```

Do not assume this architecture is automatically correct.

**Challenge it.**

If you believe another architecture would be more appropriate for the scope of this project, explain why and make me choose.

---

# 5. Database

PostgreSQL should be the primary relational database.

The database design should demonstrate:

* Proper relationships
* Foreign keys
* Constraints
* Indexes
* Transactions
* Unique constraints
* Appropriate normalization
* Database migrations

Do not immediately create the final schema.

First help me reason about:

* entities
* relationships
* cardinality
* ownership
* lifecycle
* constraints
* business invariants

Then produce the schema after we have validated the domain model.

---

# 6. API

The API should follow REST principles.

Examples:

```text
GET    /api/customers
GET    /api/customers/{id}
POST   /api/customers
PUT    /api/customers/{id}
DELETE /api/customers/{id}
```

Orders:

```text
GET  /api/orders
GET  /api/orders/{id}
POST /api/orders
POST /api/orders/{id}/confirm
POST /api/orders/{id}/cancel
```

Inventory:

```text
GET  /api/inventory
GET  /api/inventory/movements
POST /api/inventory/adjustments
```

Dashboard:

```text
GET /api/dashboard/summary
GET /api/dashboard/revenue
GET /api/dashboard/expenses
GET /api/dashboard/top-products
```

Again, challenge these endpoints.

I want to understand when a standard CRUD endpoint is appropriate versus when a domain-specific operation such as:

```text
POST /orders/{id}/confirm
```

is preferable.

---

# 7. Testing Strategy

Testing should be part of the architecture rather than something added at the end.

Backend:

* Unit tests
* Service/business-rule tests
* Controller tests where useful
* Integration tests
* PostgreSQL integration using Testcontainers if appropriate

Examples:

```text
Should not confirm cancelled order

Should not sell more than available stock

Should calculate order total correctly

Should create inventory movement after sale

Should reject negative product price
```

Frontend:

* Component tests
* Form validation tests
* User interaction tests
* Loading/error state tests
* Selected E2E flows

One important E2E scenario should eventually be:

```text
Login
  ↓
Create customer
  ↓
Create product
  ↓
Create order
  ↓
Confirm order
  ↓
Inventory decreases
  ↓
Financial record is created
  ↓
Dashboard reflects the change
```

---

# 8. Local Development

The project should be runnable locally.

Prefer Docker Compose for infrastructure.

Conceptually:

```text
Next.js
localhost:3000
       ↓
Spring Boot
localhost:8080
       ↓
PostgreSQL
localhost:5432
```

Optional tools such as pgAdmin can be considered.

The project should have clear environment configuration and setup instructions.

---

# 9. Portfolio Goals

This is not intended to be a commercial production ERP.

It is a **portfolio project designed to demonstrate professional engineering ability**.

I want the finished project to demonstrate understanding of:

### Java / Spring Boot

* REST APIs
* Dependency injection
* DTOs
* Validation
* Exception handling
* Transactions
* JPA/Hibernate
* Relationships
* Pagination
* Security
* Testing

### PostgreSQL

* Relational modeling
* Foreign keys
* Constraints
* Indexes
* Transactions
* Aggregations
* Migrations

### Next.js

* App Router
* Server Components
* Client Components when necessary
* Forms
* Data fetching
* Loading/error states
* Route protection
* Component architecture

### Engineering

* Git
* Clean architecture
* Feature organization
* Testing
* Docker
* Documentation
* API contracts
* Environment configuration

---

# 10. How I Want You To Work With Me

Use the **grill-me-with-docs** methodology.

Do NOT immediately generate all documentation.

Instead:

### Step 1 — Requirements discovery

Ask me questions about:

* target business
* users
* business processes
* scope
* assumptions
* important workflows
* non-functional requirements

Challenge vague requirements.

If I say something like:

> "The manager can manage orders."

Ask me what "manage" actually means.

---

### Step 2 — Domain modeling

Help me identify:

* entities
* value objects where appropriate
* relationships
* aggregates where appropriate
* states
* business invariants
* ownership
* lifecycle

Challenge entities that exist only because they are easy to turn into database tables.

---

### Step 3 — Architecture

Challenge:

* backend architecture
* frontend architecture
* API boundaries
* module boundaries
* transaction boundaries
* authentication
* authorization
* data fetching
* error handling

Ask me to justify important architectural decisions.

---

### Step 4 — Database design

Guide me through:

```text
ERD
↓
Tables
↓
Relationships
↓
Constraints
↓
Indexes
↓
Migrations
```

Do not optimize prematurely.

---

### Step 5 — API design

Help me define:

* endpoints
* HTTP methods
* request DTOs
* response DTOs
* validation
* error responses
* pagination
* filtering
* sorting
* authentication requirements

Challenge endpoints that leak implementation details.

---

### Step 6 — Frontend architecture

Help me define:

* routes
* layouts
* feature boundaries
* shared components
* forms
* data fetching
* loading states
* error states
* authorization behavior

Challenge unnecessary client components and unnecessary global state.

---

### Step 7 — Testing strategy

Help me determine:

* what deserves unit tests
* what deserves integration tests
* what deserves E2E tests
* what should not be tested
* where mocks are appropriate
* where real PostgreSQL should be used

---

### Step 8 — Implementation roadmap

Only after the previous decisions are reasonably stable, create a phased implementation plan.

Each phase should contain:

```text
Goal
Requirements
Backend work
Database work
Frontend work
Tests
Documentation
Definition of Done
```

---

# 11. Grill Me

I specifically want you to **challenge me instead of agreeing with me**.

When I propose something:

1. Determine whether it is technically reasonable.
2. Identify hidden problems.
3. Ask why I chose it.
4. Explain relevant tradeoffs.
5. Ask me to revise it if necessary.
6. Only then document the decision.

Do not protect me from difficult questions.

Treat this like a real architecture/design review.

However, remember that I am building this as a **portfolio project**, so avoid introducing enterprise complexity merely for the sake of appearing sophisticated.

If something is unnecessary for this scope, tell me.

---

# 12. Documentation to Produce

Eventually I want the project documentation to include:

```text
README.md

docs/
├── requirements/
│   └── requirements.md
│
├── architecture/
│   ├── architecture.md
│   └── decisions/
│
├── domain/
│   ├── domain-model.md
│   └── business-rules.md
│
├── database/
│   └── database-design.md
│
├── api/
│   └── api-design.md
│
├── frontend/
│   └── frontend-architecture.md
│
├── testing/
│   └── testing-strategy.md
│
└── development/
    └── roadmap.md
```

Use ADRs for important architectural decisions.

For example:

```text
ADR-001: Use Spring Boot for the backend
ADR-002: Use PostgreSQL as the primary database
ADR-003: Use REST for frontend/backend communication
ADR-004: Use feature-oriented backend packages
ADR-005: Use Next.js App Router
ADR-006: Use inventory movements instead of direct stock mutations
```

Do not create ADRs just for trivial decisions.

---

# 13. First Task

Start by **grilling me about the business itself**.

Do not create the database.

Do not create Java classes.

Do not create Next.js components.

Do not write implementation code.

Do not assume that every requirement above is final.

Instead, begin the project discovery process with the **highest-value questions needed to turn this idea into a coherent domain model**.

Ask questions **one at a time**, wait for my answer, challenge my answer when appropriate, and progressively build the project documentation from my decisions.

At the end of each major discovery stage, provide a concise summary of the decisions we've made and identify unresolved questions.

The objective is to finish with a project that I can actually implement while also being able to explain **why it was designed this way in a technical interview**.
