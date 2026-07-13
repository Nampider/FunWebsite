package com.familyProject.Infosite.controller;

import com.familyProject.Infosite.dto.OutgoingChatMessage;
import com.familyProject.Infosite.interfaces.ChatMessageStore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/messages")
public class ChatHistoryController {

    private final ChatMessageStore chatMessageStore;

    public ChatHistoryController(
            ChatMessageStore chatMessageStore) {

        this.chatMessageStore =
                chatMessageStore;
    }

    @GetMapping
    public List<OutgoingChatMessage> recentMessages(

            @RequestParam(defaultValue = "50")
            @Min(1)
            @Max(100)
            int limit) {

        return chatMessageStore.recent(limit);
    }
}
