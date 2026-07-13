package com.familyProject.Infosite.chat;

import com.familyProject.Infosite.dto.ChatProperties;
import com.familyProject.Infosite.dto.OutgoingChatMessage;
import com.familyProject.Infosite.enums.ChatMessageType;
import com.familyProject.Infosite.interfaces.ChatMessageStore;
import org.springframework.boot.autoconfigure.condition
        .ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Service
@ConditionalOnProperty(
        name = "chat.persistence",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryChatMessageStore
        implements ChatMessageStore {

    private final Deque<OutgoingChatMessage> messages =
            new ArrayDeque<>();

    private final ReentrantLock lock =
            new ReentrantLock();

    private final int historyLimit;

    public InMemoryChatMessageStore(
            ChatProperties properties) {

        this.historyLimit =
                properties.inMemoryHistoryLimit();
    }

    @Override
    public OutgoingChatMessage save(
            String username,
            String text) {

        OutgoingChatMessage message =
                new OutgoingChatMessage(
                        UUID.randomUUID().toString(),
                        ChatMessageType.CHAT,
                        username,
                        text,
                        Instant.now()
                );

        lock.lock();

        try {
            messages.addLast(message);

            while (messages.size() > historyLimit) {
                messages.removeFirst();
            }
        } finally {
            lock.unlock();
        }

        return message;
    }

    @Override
    public List<OutgoingChatMessage> recent(
            int limit) {

        lock.lock();

        try {
            List<OutgoingChatMessage> snapshot =
                    new ArrayList<>(messages);

            int fromIndex = Math.max(
                    0,
                    snapshot.size() - limit
            );

            return List.copyOf(
                    snapshot.subList(
                            fromIndex,
                            snapshot.size()
                    )
            );
        } finally {
            lock.unlock();
        }
    }
}
