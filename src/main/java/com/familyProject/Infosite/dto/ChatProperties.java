package com.familyProject.Infosite.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "chat")
public record ChatProperties(

        @NotBlank
        String persistence,

        @NotEmpty
        List<@NotBlank String> allowedOrigins,

        @Min(1)
        @Max(5000)
        int maxMessageLength,

        @Min(1)
        @Max(1000)
        int inMemoryHistoryLimit,

        @Min(256)
        @Max(65_536)
        int maxFrameSizeBytes,

        @Min(1)
        @Max(100)
        int maxMessagesPerSecond,

        @NotEmpty
        List<@Valid AllowedUser> users

) {

    @AssertTrue(message = "chat.max-frame-size-bytes is too small for chat.max-message-length")
    public boolean isFrameLargeEnoughForMessageLimit() {
        return maxFrameSizeBytes >= (maxMessageLength * 6L) + 128;
    }

    public record AllowedUser(

            @NotBlank
            @Pattern(
                    regexp = "^[a-z0-9_-]{3,24}$",
                    message = """
                            Username must contain 3-24 lowercase letters, \
                            numbers, underscores, or hyphens
                            """
            )
            String username,

            @NotBlank
            @Size(min = 8, max = 72)
            String password

    ) {

        @AssertTrue(message = "Password must not exceed 72 UTF-8 bytes")
        public boolean isPasswordWithinBcryptLimit() {
            return password == null
                    || password.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
    }
}
