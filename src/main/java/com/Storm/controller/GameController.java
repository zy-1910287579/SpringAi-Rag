package com.Storm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("ai")
public class GameController {

    private final ChatClient gameChatClient;

    @RequestMapping(value = "game")
    public Flux<String> chat(String prompt, String chatId) {

        log.info("Game request - chatId: {}, prompt: {}", chatId, prompt);

        if (chatId == null || chatId.isBlank()) {
            log.warn("chatId is empty! This will cause memory issues.");
        }

        return gameChatClient.prompt()
                .user(prompt)
                .advisors(a->a.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .stream()
                .content();
    }
}