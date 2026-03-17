package com.Storm.controller;

import com.Storm.entity.vo.MessageVO;
import com.Storm.exception.BusinessException;
import com.Storm.exception.ParamValidationException;
import com.Storm.exception.ResourceNotFoundException;
import com.Storm.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("ai/history")
public class ChatHistoryController {

    private final ChatHistoryRepository chatHistoryRepository;

    private final ChatMemory chatMemory;
    // 允许的type值（贴合你的业务：chat/service/pdf/game）
    private static final List<String> ALLOWED_TYPES = List.of("chat", "service", "pdf", "game");


    @GetMapping("{type}")
    public List<String> getChatIds(@PathVariable String type){
        // 1. 入参校验：type非空+合法
        if (type == null || type.isBlank()) {
            throw new ParamValidationException("会话类型不能为空（支持：chat/service/pdf/game）");
        }
        if (!ALLOWED_TYPES.contains(type)) {
            log.warn("非法的会话类型请求：{}，允许类型：{}", type, ALLOWED_TYPES);
            throw new ParamValidationException("会话类型不合法（支持：chat/service/pdf/game）");
        }
        // 2. 核心操作捕获异常，包装成自定义异常
        try {
            List<String> chatIds = chatHistoryRepository.getChatIds(type);
            // 空结果友好处理（返回空列表，保持和原逻辑一致）,避免chatHistoryRepository.getChatIds()返回 null 导致空指针
            return Objects.requireNonNullElse(chatIds, List.of());
        /*重要知识点*/
        /** 关于为什么这里catch(BusinessException e),其实是try的代码块可能会隐式抛出自定义异常
         * 比如这里的 "chatHistoryRepository.getChatIds(type)";
         * 怎么加看下面!!!
         *  1.先看 try 块里的代码：有没有调用「自己写的 Service/Repository/ 工具类」→ 有 → 加对应自定义异常的 catch；
            2.再预判未来扩展：如果这个方法是核心业务接口（比如查询会话历史），未来大概率加业务逻辑 → 提前加（比如 BusinessException）；
            3.最后做减法：如果只有纯 JDK / 框架原生代码（无业务调用）→ 删掉冗余的 catch 分支。**/
        } catch (BusinessException e) {
            throw e; // 自定义异常直接抛
        } catch (Exception e) {
            log.error("查询会话ID列表失败，类型：{}", type, e);
            throw new BusinessException(500, "查询会话ID列表失败，请稍后重试");
        }
    }

    @GetMapping("{type}/{chatId}")
    public List<MessageVO> getChatHistory(@PathVariable String type, @PathVariable String chatId){
        // 1. 入参校验：type+chatId双校验
        if (type == null || type.isBlank()) {
            throw new ParamValidationException("会话类型不能为空（支持：chat/service/pdf/game）");
        }
        if (!ALLOWED_TYPES.contains(type)) {
            log.warn("非法的会话类型请求：{}，允许类型：{}", type, ALLOWED_TYPES);
            throw new ParamValidationException("会话类型不合法（支持：chat/service/pdf/game）");
        }
        if (chatId == null || chatId.isBlank()) {
            throw new ParamValidationException("会话ID不能为空");
        }

        try {
            List<Message> messages = chatMemory.get(chatId, Integer.MAX_VALUE);
            // 资源不存在：主动抛404异常（ResourceNotFoundException），而非返回空列表
            if (messages == null || messages.isEmpty()) {
                log.info("会话历史不存在，类型：{}，chatId：{}", type, chatId);
                throw new ResourceNotFoundException("该会话暂无历史记录");
            }
            return messages.stream().map(MessageVO::new).toList();
        } catch (ResourceNotFoundException e) {
            throw e; // 资源不存在异常直接抛
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询会话历史失败，类型：{}，chatId：{}", type, chatId, e);
            throw new BusinessException(500, "查询会话历史失败，请稍后重试");
        }
    }


}
