package com.Storm.controller;


import com.Storm.exception.BusinessException;
import com.Storm.repository.ChatHistoryRepository;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
@Slf4j // 仅新增日志注解，用于记录异常详情
@RequiredArgsConstructor
@RestController
@Validated // 新增：类上加，触发@RequestParam的注解校验
@RequestMapping("ai")
public class CustomerServiceController {
    private final ChatClient serviceChatClient;

    private final ChatHistoryRepository chatHistoryRepository;
    @RequestMapping("service")
    public String service(//requestparam就不加了,参数名称和 ,前端保持一致就可
            @NotBlank(message = "提问内容不能为空") String prompt,
            @NotBlank(message = "会话id不能为空") String chatId) {
        /**入参都要有校验!!!
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(400, "提问内容不能为空");
        }
        if (chatId == null || chatId.isBlank()) {
            throw new BusinessException(400, "会话ID不能为空");
        }*/

        // 操作2：调用AI模型（保留原有try-catch逻辑，仅删除手动校验）
        try {
            return serviceChatClient
                    .prompt()
                    .user(prompt)
                    .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                    .call()
                    .content();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // prompt可能包含敏感信息/过长，日志可截断（比如只取前50个字符）
            String shortPrompt = prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt;
            log.error("AI模型调用失败，chatId：{}，prompt：{}", chatId, shortPrompt, e);
            // 脱敏：不返回e.getMessage()，避免暴露AI模型内部错误（比如API key失效、网络超时等）
            throw new BusinessException(500, "AI服务暂时不可用，请稍后重试");
        }
    }
}
