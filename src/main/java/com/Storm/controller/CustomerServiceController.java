package com.Storm.controller;


import com.Storm.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@RequiredArgsConstructor
@RestController
@RequestMapping("ai")
public class CustomerServiceController {
    private final ChatClient serviceChatClient;

    private final ChatHistoryRepository chatHistoryRepository;
    @RequestMapping("service")
    public String service(String prompt, String chatId) {
        //1.先保存会话id到会话内存中
        chatHistoryRepository.save("service",chatId);
        //2.在请求模型
        return serviceChatClient
                .prompt()
                .user(prompt)
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .call()
                .content();
    }
}
