package com.example.bms.shared;

import java.util.List;

/**
 * The paged envelope every collection answers with (api-design): Spring's
 * {@code Page} never leaks — these five keys are the contract the frontend
 * table binds to.
 */
public record PagedEnvelope<T>(List<T> items, int page, int size, long total, int totalPages) {
}
