package com.familyProject.Infosite.dto;

public record AuthResponse(
        boolean authenticated,
        String username
) {
}
