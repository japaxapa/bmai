# Use PostgreSQL as the primary database

Postgres is the system of record: FKs, unique/document constraints, CHECKs (stock ≥ 0, price > 0), native enums, row locks for the confirm race, and aggregate dashboard queries. The brief demands demonstrating relationships, constraints, indexes, transactions, and migrations — Postgres does all of them, and Neon gives a free tier for the optional deploy.
