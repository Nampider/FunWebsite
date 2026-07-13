package com.familyProject.Infosite.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@ConditionalOnProperty(
        name = "chat.debug-endpoints-enabled",
        havingValue = "true"
)
public class ThreadDebugController {

    @GetMapping("/thread")
    public Map<String, Object> currentThread() {
        Thread thread =
                Thread.currentThread();

        return Map.of(
                "threadName",
                thread.getName(),

                "threadId",
                thread.threadId(),

                "virtual",
                thread.isVirtual()
        );
    }
}
