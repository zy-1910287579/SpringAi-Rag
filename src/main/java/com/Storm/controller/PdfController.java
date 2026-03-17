package com.Storm.controller;

import com.Storm.entity.vo.Result;
import com.Storm.exception.BusinessException;
import com.Storm.repository.ChatHistoryRepository;
import com.Storm.repository.IFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;


@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/pdf")
public class PdfController {


    private final IFileService fileService;

    private final ChatClient pdfChatClient;

    private final ChatHistoryRepository chatHistoryRepository;



    @RequestMapping("chat")
    public Flux<String> chat(String prompt, String chatId) {
        //这里要注意文件可能不存在,要抛出自定义异常
        Resource file = fileService.getFile(chatId);
        if (!file.exists()) {
            throw new BusinessException(404, "文件不存在！");
        }
        chatHistoryRepository.save("pdf",chatId);
        //2.在请求模型
        return pdfChatClient
                .prompt()
                .user(prompt)
                .advisors(
                        advisorSpec -> advisorSpec.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .advisors(advisorSpec -> advisorSpec.param(QuestionAnswerAdvisor.FILTER_EXPRESSION,"file_name=='"+file.getFilename()+"'"))
                .stream()
                .content();
    }


    @RequestMapping("/upload/{chatId}")
    public Result uploadPdf(@PathVariable String chatId, @RequestParam("file") MultipartFile file) {
        try {
            // 1. 校验文件是否为PDF格式
            if (!Objects.equals(file.getContentType(), "application/pdf")) {
                return Result.fail("只能上传PDF文件！");
            }

            // 2.保存文件
            boolean success = fileService.save(chatId, file.getResource());
            if(! success) {
                return Result.fail("保存文件失败！");
            }
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to upload PDF.", e);
            return Result.fail("上传文件失败！");
        }
    }


    @GetMapping("/file/{chatId}")
    public ResponseEntity<Resource> download(@PathVariable("chatId") String chatId) throws IOException {
        // 1.读取文件
        //IO异常会自动抛给全局异常处理器的其他异常分类,背exception捕获，返回500+Result格式）
        Resource resource = fileService.getFile(chatId);
        //2. 业务逻辑判断：文件不存在返回404（这是预期的业务场景，不是异常）
        if (!resource.exists()) {
            // 返回404响应（这是RESTful规范，不属于异常，无需走全局异常）
            return ResponseEntity.notFound().build();
        }
        // 3. 可选：校验文件可读，若不可读则抛自定义业务异常，走全局异常处理器
        if (!resource.isReadable()) {
            throw new BusinessException(403, "文件不可读，无访问权限");
        }
        // 2.文件名编码，写入响应头
        String filename = URLEncoder.encode(Objects.requireNonNull(resource.getFilename()), StandardCharsets.UTF_8);
        // 3.返回文件
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }


}