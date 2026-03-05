package com.Storm.controller;

import com.Storm.entity.vo.MessageVO;
import com.Storm.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("ai/history")
public class ChatHistoryController {

    private final ChatHistoryRepository chatHistoryRepository;

    private final ChatMemory chatMemory;

    @GetMapping("{type}")
    public List<String> getChatIds(@PathVariable String type){
        return chatHistoryRepository.getChatIds(type);
    }

    @GetMapping("{type}/{chatId}")
    public List<MessageVO> getChatHistory(@PathVariable String type, @PathVariable String chatId){
        List<Message> messages = chatMemory.get(chatId,Integer.MAX_VALUE);
        if(messages==null){
            return List.of();
        }
        return messages.stream().map(MessageVO::new).toList();
    }


}
