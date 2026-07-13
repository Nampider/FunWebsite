package com.familyProject.Infosite.dto;

import java.time.Instant;

public record ApiError(
        String code,
        String message,
        Instant timestamp
) {
}
