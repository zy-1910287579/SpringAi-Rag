package com.Storm.controller;
import com.Storm.exception.BusinessException;
import com.Storm.exception.ThirdPartyApiException;
import com.Storm.repository.ChatHistoryRepository;

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
@RequestMapping("/ai")
public class ChatController {


    private final ChatClient chatClient;

    private final ChatHistoryRepository chatHistoryRepository;

    @RequestMapping("chat")
    public Flux<String> chat(String prompt,String chatId) {

        //入参都要有校验!!!
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(400, "提问内容不能为空");
        }
        if (chatId == null || chatId.isBlank()) {
            throw new BusinessException(400, "会话ID不能为空");
        }
        // 2. 拆分try-catch：会话记录保存单独捕获，精准定位
        try {
            chatHistoryRepository.save("chat", chatId);
        } catch (BusinessException e) {
            throw e; // 自定义异常直接抛
        } catch (Exception e) {
            /**日志带上下文（chatId），方便排查,保存失败要看到是哪个会话记录保存失败啊,对吧**/
            /*面试高频考点：“异常日志要包含哪些信息？”—— 必须有业务上下文（chatId/prompt）+ 完整堆栈，缺一不可*/
            log.error("会话记录保存失败，chatId：{}", chatId, e);
            throw new BusinessException(500, "会话记录保存失败，请稍后重试");
        }

        //3.在请求模型
        /**在主流的 Java 后端开发（如 Spring Boot）中，
        这个 “Java 转 JSON” 的过程几乎是自动的，不需要你手动拼接 JSON 字符串，框架会帮你完成：
        @RestController（或 @Controller + @ResponseBody），Spring Boot 会默认使用 Jackson 库，
        把你返回的 Java 对象 / 集合自动转换成 JSON 字符串，并设置响应头 Content-Type: application/json。**/
        try {
            return chatClient
                    .prompt()
                    .user(prompt)
                    .advisors(advisorSpec -> advisorSpec.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                    .stream()
                    .content();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 日志带完整上下文（chatId+prompt），截断长prompt避免日志过大
            String shortPrompt = prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt;
            log.error("AI通用问答服务调用失败，chatId：{}，prompt：{}", chatId, shortPrompt, e);
            // 大模型调用失败属于“第三方API问题”，用ThirdPartyApiException更精准（502错误码）
            /**500 = 系统内部错（比如保存会话失败），502 = 第三方服务错（比如大模型调用失败），前端可针对性提示**/
            throw new ThirdPartyApiException("AI通用问答服务暂时不可用，请稍后重试");
        }

    }


}
