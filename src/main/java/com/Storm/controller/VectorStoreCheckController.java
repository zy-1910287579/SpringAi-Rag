package com.Storm.controller;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class VectorStoreCheckController {

    // 注入你定义的 vectorStore Bean
    @Autowired
    private VectorStore vectorStore;

    /**
     * 接口：查询向量库中所有存储的文档片段
     * 访问地址：http://localhost:8080/vector-store/list
     */
    @GetMapping("/vector-store/list")
    public String listVectorStoreDocs() {
        // 1. 获取向量库中所有文档（包含内容、元数据、向量）
        List<Document> documents = vectorStore.similaritySearch(""); // 空字符串匹配所有

        // 2. 无数据则直接提示
        if (documents.isEmpty()) {
            return "向量库中暂无数据！PDF未被向量化存储";
        }

        // 3. 拼接数据打印（重点看文档内容和向量长度）
        StringBuilder sb = new StringBuilder();
        sb.append("✅ 向量库中存储的文档数量：").append(documents.size()).append("<br>");
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            sb.append("===== 文档片段 ").append(i+1).append(" =====<br>");
            sb.append("文档内容：").append(doc.getText()).append("<br>"); // PDF的文本片段
            sb.append("向量长度：").append(doc.getMetadata() == null ? "无向量" : doc.getMetadata().size()).append("<br>"); // 向量长度（OpenAI嵌入是1536）
            sb.append("元数据：").append(doc.getMetadata()).append("<br><br>");
        }
        return sb.toString();
    }
}