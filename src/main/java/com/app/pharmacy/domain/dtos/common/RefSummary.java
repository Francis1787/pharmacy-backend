package com.app.pharmacy.domain.dtos.common;

import java.util.UUID;

/**
 * Lightweight reference to another entity, used inside response DTOs
 * so nested associations (e.g. "which drug", "which staff member")
 * don't require serializing the full related object graph.
 */
public record RefSummary(
        UUID id,
        String label
) {
}
