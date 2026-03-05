package com.Storm.repository;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChatHistoryRepositoryImpl implements ChatHistoryRepository{

    // TODO,这个要换成redis缓存改进
    //在内存中保存聊天记录,就是用数据结构存储
    private  final  Map<String,List< String>> chatHistory= new HashMap<>();

    @Override
    public void save(String type, String userId) {
        // 1. 先按模块类型分类 (chat, game, service, pdf 等)
        if( !chatHistory.containsKey(type)){
            chatHistory.put(type,new ArrayList<>());
        }

        // 2. 获取该模块下的所有聊天窗口 ID 列表
        List<String> chatIds = chatHistory.get(type);

        // 3. 【关键逻辑】
        // - 前端为每个新聊天窗口生成一个不变的 userId(chatId)
        // - 用户在这个窗口中每次对话都会调用 save()
        // - 第一次对话：userId 不存在 → 添加到列表 ✓
        // - 后续对话：userId 已存在 → 直接返回，后续具体内容查询不看这里,这里只是单纯的记录每个窗口的id
        if(chatIds.contains(userId)){
            return;  // 这个窗口的 ID 已经记录过了，不需要重复存储
        }

        // 4. 只有首次进入这个窗口的对话，才会执行到这里
        chatIds.add(userId);
    }

    @Override
    public List<String> getChatIds(String type) {
        List<String> chatIds = chatHistory.get(type);

        //有可能为空, 所以要判断
        return chatIds==null ? List.of()  :chatIds;
    }
}
