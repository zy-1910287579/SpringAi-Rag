package com.Storm.repository;

import java.util.List;

public interface ChatHistoryRepository {


    // 保存聊天记录, type: 聊天类型, userId: 用户id
    void save(String type,String userId);


    // 查询聊天记录, type: 聊天类型
    List<String> getChatIds(String type);
}
