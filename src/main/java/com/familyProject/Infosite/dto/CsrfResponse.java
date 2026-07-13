package com.familyProject.Infosite.dto;

public record CsrfResponse(
        String headerName,
        String parameterName,
        String token
) {
}
