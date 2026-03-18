package com.Storm.repository;
import com.Storm.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
@Validated
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements IFileService {


    // ========== 基础优化：读取配置文件中的PDF存储配置 ==========
    @Value("${pdf.storage.base-path}")
    private String pdfBasePath;

    @Value("${pdf.storage.charset:UTF-8}")
    private String charset;

    @Value("${pdf.storage.auto-create-dir:true}")
    private boolean autoCreateDir;

    private final VectorStore vectorStore;
    // 会话id 与 文件路径的对应关系，方便查询会话历史时重新加载文件
    private final Properties chatFiles = new Properties();

    //总结Resource的核心价值（对应你的 PDF 场景）
    //简化操作：把本地文件的 “路径拼接、状态判断、流创建” 等繁琐操作封装成简洁的 API（比如 getFilename() 直接拿文件名，exists() 直接判断存在性）；
    //统一抽象：不管文件存在本地、类路径、网络，都用同一套方法操作，后续改存储方式不用改业务逻辑；
    //适配 Spring 生态：能直接作为控制器返回值，实现文件下载，完美契合你的 PDF 下载功能。


    @Override
    public boolean save(
            @NotBlank(message = "会话ID不能为空")
            String chatId, Resource resource) {// Resource无法用注解校验，仍需手动

        if (resource == null || !resource.exists()) {
            throw new BusinessException(400, "文件资源不存在");
        }
        // 2. 获取文件名，空值抛自定义异常（替代Objects.requireNonNull避免NPE）
        //resource.getFilename() 返回的是原始文件名（含后缀），不是我们凭空生成的，而是上传时自带的。
        String filename = resource.getFilename();
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(400, "文件名称为空，无法保存");
        }

        // ========== 核心优化1：构建结构化存储路径 ==========
        // 2. 构建会话专属目录（避免文件冲突）：basePath/chatId/

        //把父路径和子路径进行拼接,创建一个File对象!!
        File chatDir = new File(pdfBasePath, chatId);
        // 自动创建目录（包括父目录）
        if (autoCreateDir && !chatDir.exists()) {
            boolean mkdirsSuccess = chatDir.mkdirs();
            if (!mkdirsSuccess) {
                throw new BusinessException(500, "创建会话存储目录失败：" + chatDir.getAbsolutePath());
            }
            log.info("创建会话专属存储目录成功：{}", chatDir.getAbsolutePath());
        }

        // 3. 处理安全文件名（解决中文/特殊字符乱码、路径注入）
        String safeFilename = getSafeFilename(filename);
        // 4. 构建文件完整路径：basePath/chatId/安全文件名
        File targetFile = new File(chatDir, safeFilename);


        // 3. 保存文件到本地磁盘，IO异常抛自定义业务异常
        //判断文件是否存在
        // 5. 保存文件到结构化路径（原有逻辑，路径替换）
        try {
            if (!targetFile.exists()) {
                Files.copy(resource.getInputStream(), targetFile.toPath());
                log.info("文件保存成功，路径：{}，会话ID：{}", targetFile.getAbsolutePath(), chatId);
            }
        } catch (IOException e) {
            log.error("保存PDF文件IO失败，路径：{}，会话ID：{}", targetFile.getAbsolutePath(), chatId, e);
            throw new BusinessException(500, "文件保存失败：磁盘写入异常");
        } catch (SecurityException e) {
            log.error("保存PDF文件权限不足，路径：{}，会话ID：{}", targetFile.getAbsolutePath(), chatId, e);
            throw new BusinessException(403, "文件保存失败：无写入权限");
        }

        // ========== 核心优化2：存储文件完整路径（而非仅文件名） ==========]
        //保存映射关系
        chatFiles.put(chatId, targetFile.getAbsolutePath());
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
    public Resource getFile(@NotBlank(message = "会话ID不能为空") String chatId) {

        // ========== 核心优化3：读取文件完整路径（而非仅文件名） ==========
        // 2. 获取文件名，不存在抛404自定义异常
        String filePath = chatFiles.getProperty(chatId);
        if (filePath == null || filePath.isBlank()) {
            throw new BusinessException(404, "未找到该会话对应的文件");
        }

        // 3. 校验文件是否存在，不存在抛404
        FileSystemResource resource = new FileSystemResource(filePath);
        if (!resource.exists() || !resource.isReadable()) {
            throw new BusinessException(404, "文件不存在或不可读：" + filePath);
        }
        // 补充读取权限校验
        try {
            if (!resource.getFile().canRead()) {
                throw new BusinessException(403, "文件无读取权限：" + filePath);
            }
        } catch (Exception e) {
            log.error("校验文件读取权限失败，路径：{}", filePath, e);
            throw new BusinessException(500, "文件权限校验异常");
        }
        return resource;
    }

    @PostConstruct
    private void init() {
        // ========== 新增：启动时校验PDF基础存储路径可写性 ==========
        File baseDir = new File(pdfBasePath);
        if (autoCreateDir && !baseDir.exists()) {
            baseDir.mkdirs();
        }
        if (!baseDir.canWrite()) {
            throw new BusinessException(500, "PDF存储基础路径无写入权限：" + baseDir.getAbsolutePath());
        }
        log.info("PDF存储基础路径校验通过：{}（可写）", baseDir.getAbsolutePath());

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
            PagePdfDocumentReader reader = new PagePdfDocumentReader(resource,buildPdfReaderConfig());

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
    // 修复7：抽离PDF Reader配置，避免重复代码，同时统一配置
    private PdfDocumentReaderConfig buildPdfReaderConfig() {
        return PdfDocumentReaderConfig.builder()
                .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                .withPagesPerDocument(1) // 每1页作为一个Document
                .build();
    }

    //========== 基础优化：新增安全文件名处理方法 ==========//
    /**
     * 处理文件名中的特殊字符，解决中文乱码、路径注入问题
     */
    private String getSafeFilename(String filename) {
        if (filename == null) {
            return "unknown.pdf";
        }
        // 1. 替换路径注入特殊字符（\ / : * ? " < > |）为下划线
        String safeName = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        // 2. 统一字符编码（解决中文乱码）
        try {
            return new String(safeName.getBytes(charset), charset);
        } catch (UnsupportedEncodingException e) {
            log.warn("文件名编码转换失败，使用默认编码：{}", filename, e);
            return safeName;
        }
    }
}
