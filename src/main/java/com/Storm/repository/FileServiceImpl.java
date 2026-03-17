package com.Storm.repository;

import com.Storm.exception.BusinessException;
import com.Storm.repository.IFileService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements IFileService {

    private final VectorStore vectorStore;

    // 会话id 与 文件名的对应关系，方便查询会话历史时重新加载文件
    private final Properties chatFiles = new Properties();

    //总结Resource的核心价值（对应你的 PDF 场景）
    //简化操作：把本地文件的 “路径拼接、状态判断、流创建” 等繁琐操作封装成简洁的 API（比如 getFilename() 直接拿文件名，exists() 直接判断存在性）；
    //统一抽象：不管文件存在本地、类路径、网络，都用同一套方法操作，后续改存储方式不用改业务逻辑；
    //适配 Spring 生态：能直接作为控制器返回值，实现文件下载，完美契合你的 PDF 下载功能。
    @Override
    public boolean save(String chatId, Resource resource) {
        // 1. 校验入参，空值抛自定义业务异常
        if (chatId == null || chatId.isBlank()) {
            throw new BusinessException(400, "会话ID不能为空");
        }

        if (resource == null || !resource.exists()) {
            throw new BusinessException(400, "文件资源不存在");
        }
        // 2. 获取文件名，空值抛自定义异常（替代Objects.requireNonNull避免NPE）
        String filename = resource.getFilename();
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(400, "文件名称为空，无法保存");
        }
        File target = new File(filename);

        // 3. 保存文件到本地磁盘，IO异常抛自定义业务异常
        //判断文件是否存在
        try {
            if (!target.exists()) {
                Files.copy(resource.getInputStream(), target.toPath());
                log.info("文件保存成功，文件名：{}，会话ID：{}", filename, chatId);
            }
        } catch (IOException e) {
            log.error("保存PDF文件失败，文件名：{}，会话ID：{}", filename, chatId, e);
            throw new BusinessException(500, "文件保存失败：" + e.getMessage());
        }


        // 2.保存映射关系
        chatFiles.put(chatId, filename);
        //3. 写入向量库
        try {
            writeToVectorStore(resource, chatId);
        } catch (Exception e) {
            log.error("PDF写入向量库失败，会话ID：{}", chatId, e);
            throw new BusinessException(500, "文件解析失败：" + e.getMessage());
        }
        return true;
    }


    @Override
    public Resource getFile(String chatId) {
        // 1. 校验会话ID
        if (chatId == null || chatId.isBlank()) {
            throw new BusinessException(400, "会话ID不能为空");
        }

        // 2. 获取文件名，不存在抛404自定义异常
        String filename = chatFiles.getProperty(chatId);
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(404, "未找到该会话对应的文件");
        }

        // 3. 校验文件是否存在，不存在抛404
        FileSystemResource resource = new FileSystemResource(filename);
        if (!resource.exists() || !resource.isReadable()) {
            throw new BusinessException(404, "文件不存在或不可读：" + filename);
        }
        return resource;
    }

    @PostConstruct
    private void init() {
        // 1. 加载会话-文件映射关系
        FileSystemResource pdfResource = new FileSystemResource("chat-pdf.properties");
        if (pdfResource.exists()) {
            try {
                chatFiles.load(new BufferedReader(new InputStreamReader(pdfResource.getInputStream(), StandardCharsets.UTF_8)));
                log.info("成功加载会话-文件映射关系，共{}条记录", chatFiles.size());
            } catch (IOException e) {
                log.error("加载chat-pdf.properties失败", e);
                throw new BusinessException(500, "系统初始化失败：文件映射关系加载失败");
            }
        }
        // 2. 加载向量库
        FileSystemResource vectorResource = new FileSystemResource("chat-pdf.json");
        if (vectorResource.exists()) {
            try {
                if (vectorStore instanceof SimpleVectorStore simpleVectorStore) {
                    simpleVectorStore.load(vectorResource);
                    log.info("成功加载向量库文件 chat-pdf.json");
                } else {
                    log.warn("向量库类型不是SimpleVectorStore，跳过加载");
                }
            } catch (Exception e) {
                log.error("加载向量库文件失败", e);
                throw new BusinessException(500, "系统初始化失败：向量库加载失败");
            }
        }
    }

    @PreDestroy
    private void persistent() {
        // 1. 持久化会话-文件映射关系
        try (FileWriter writer = new FileWriter("chat-pdf.properties", StandardCharsets.UTF_8)) {
            chatFiles.store(writer, "Chat-PDF Mapping " + LocalDateTime.now());
            log.info("成功持久化会话-文件映射关系，共{}条记录", chatFiles.size());
        } catch (IOException e) {
            log.error("持久化chat-pdf.properties失败", e);
            throw new BusinessException(500, "系统销毁失败：文件映射关系持久化失败");
        }
        // 2. 持久化向量库
        try {
            if (vectorStore != null && vectorStore instanceof SimpleVectorStore simpleVectorStore) {
                simpleVectorStore.save(new File("chat-pdf.json"));
                log.info("成功持久化向量库文件 chat-pdf.json");
            }
        } catch (Exception e) {
            log.error("持久化向量库文件失败", e);
            throw new BusinessException(500, "系统销毁失败：向量库持久化失败");
        }
    }

    private void writeToVectorStore(Resource resource, String chatId) {
        try {
            // 1. 创建PDF读取器，配置格式化器
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                    .withPagesPerDocument(1) // 每1页作为一个Document
                    .build();
            PagePdfDocumentReader reader = new PagePdfDocumentReader(resource, config);

            // 2. 读取PDF文档
            List<Document> documents = reader.read();
            if (documents.isEmpty()) {
                throw new BusinessException(400, "PDF文件内容为空，无法写入向量库");
            }

            // 3. 给每个文档添加会话ID元数据
            documents.forEach(document -> document.getMetadata().put("chat_id", chatId));

            // 4. 写入向量库
            vectorStore.add(documents);
            log.info("PDF文件成功写入向量库，会话ID：{}，文档数：{}", chatId, documents.size());
        } catch (BusinessException e) {
            // 自定义业务异常直接抛出
            throw e;
        } catch (Exception e) {
            // 其他异常（IO、PDF解析、向量库操作）封装为业务异常
            log.error("写入向量库失败，会话ID：{}", chatId, e);
            throw new BusinessException(500, "PDF解析或向量库写入失败：" + e.getMessage());
        }
    }
}
