# Use REST for frontend/backend communication

One Next.js client, CRUD-plus-domain-actions, no streaming or federated consumers — REST with resource routes plus action endpoints (`POST /orders/{id}/confirm`) covers it. GraphQL/tRPC would add schema and caching machinery for a single consumer; rejected. No `/v1` prefix until a second consumer exists.
