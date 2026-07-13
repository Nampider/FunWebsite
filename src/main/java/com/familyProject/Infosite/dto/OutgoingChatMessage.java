package com.familyProject.Infosite.dto;

import com.familyProject.Infosite.enums.ChatMessageType;

import java.time.Instant;

public record OutgoingChatMessage(
        String id,
        ChatMessageType type,
        String username,
        String text,
        Instant sentAt
) {
}
