package com.familyProject.Infosite.interfaces;

import com.familyProject.Infosite.dto.OutgoingChatMessage;

import java.util.List;

public interface ChatMessageStore {

    OutgoingChatMessage save(
            String username,
            String text
    );

    List<OutgoingChatMessage> recent(
            int limit
    );
}
