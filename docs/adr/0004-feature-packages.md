# Use feature-oriented backend packages

Packages are features (`order/`, `inventory/`, `finance/`, …) with layers inside, not global `controllers/`/`services/` folders. Global layers scatter one feature across six folders and let any service reach any repository. Rule: features interact only through public services. Modulith/ArchUnit enforcement is the declared stretch goal.
