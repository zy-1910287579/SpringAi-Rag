package com.Storm.controller;

import com.Storm.exception.ThirdPartyApiException;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Validated // 新增：类上加，触发@RequestParam的注解校验
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("ai")
public class GameController {

    private final ChatClient gameChatClient;

    @RequestMapping(value = "game")
    public Flux<String> chat(@NotBlank(message = "游戏提问内容不能为空") @RequestParam String prompt,
                             @NotBlank(message = "会话ID不能为空（为空会导致记忆功能异常）") @RequestParam String chatId) {

        // 修复1：日志中prompt做空值+截断防护，避免长日志/空指针
        String logPrompt = (prompt == null || prompt.isEmpty()) ? "空提问" : (prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt);
        log.info("Game request - chatId: {}, prompt: {}", chatId, prompt);
        /** ========== 删除：原chatId空值的warn日志（注解已校验非空，不会走到这里） ==========
        if (chatId == null || chatId.isBlank()) {
            log.warn("chatId is empty! This will cause memory issues.");
        }*/

        // ========== 新增：核心业务代码包trycatch（和ChatController风格一致） ==========
        try {
            return gameChatClient.prompt()
                    .user(prompt)
                    .advisors(a->a.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                    .stream()
                    .content();
        } catch (Exception e) {
            // 日志带上下文，截断长prompt避免日志过大
            // 修复2：shortPrompt做空值兜底，避免极端场景下StringIndexOutOfBoundsException
            String shortPrompt = (prompt == null || prompt.isEmpty()) ? "空提问" : (prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt);
            log.error("游戏问答服务调用失败，chatId：{}，prompt：{}", chatId, shortPrompt, e);
            // 抛你自定义的第三方API异常，和全局处理器兼容
            throw new ThirdPartyApiException("游戏问答服务暂时不可用，请稍后重试");
        }
    }
}