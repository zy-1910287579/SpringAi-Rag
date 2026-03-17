package com.Storm.controller;

import com.Storm.entity.vo.Result;
import com.Storm.exception.BusinessException;
import com.Storm.repository.ChatHistoryRepository;
import com.Storm.repository.IFileService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Validated
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/pdf")
public class PdfController {


    private final IFileService  fileService;

    private final ChatClient pdfChatClient;

    private final ChatHistoryRepository chatHistoryRepository;



    @RequestMapping("chat")
    public Flux<String> chat(
            @NotBlank(message = "PDF问答的提问内容不能为空")String prompt,
            @NotBlank(message = "PDF问答的会话ID不能为空")String chatId) {

        //这里要注意文件可能不存在,要抛出自定义异常
        Resource file = fileService.getFile(chatId);
        if (!file.exists()) {
            throw new BusinessException(404, "文件不存在！");
        }
        // 2. 核心业务逻辑包trycatch（和ChatController风格一致）
        try {
            chatHistoryRepository.save("pdf", chatId);
            return pdfChatClient
                    .prompt()
                    .user(prompt)
                    .advisors(
                            advisorSpec -> advisorSpec.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                    .advisors(advisorSpec -> advisorSpec.param(QuestionAnswerAdvisor.FILTER_EXPRESSION,"file_name=='"+file.getFilename()+"'"))
                    .stream()
                    .content();
        } catch (BusinessException e) {
            throw e; // 自定义异常直接抛，走全局处理器
        } catch (Exception e) {
            // 日志加上下文：chatId+文件名+截断的prompt，方便定位
            String shortPrompt = prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt;
            log.error("PDF问答AI调用失败，chatId：{}，文件名：{}，prompt：{}", chatId, file.getFilename(), shortPrompt, e);
            // 脱敏提示，不暴露底层错误
            throw new BusinessException(500, "PDF问答服务暂时不可用，请稍后重试");
        }
    }


    @RequestMapping("/upload/{chatId}")
    public Result uploadPdf(@PathVariable @NotBlank(message = "上传PDF的会话ID不能为空")
                                String chatId,
                            @NotNull(message = "请选择要上传的PDF文件") // 校验文件不能为空（前端没传文件时触发）
                            @RequestParam("file")
                            MultipartFile file)
    {
        try {
            // 1. 格式校验（保留）
            if (!Objects.equals(file.getContentType(), "application/pdf")) {
                return Result.fail("只能上传PDF文件！");
            }
            // 2. 核心保存逻辑
            boolean success = fileService.save(chatId, file.getResource());
            if(! success) {
                return Result.fail("保存文件失败！");
            }
            return Result.success();
        } catch (BusinessException e) {
            // 新增：捕获自定义业务异常，返回标准化Result
            log.warn("PDF上传业务校验失败，chatId：{}，文件名：{}", chatId, file.getOriginalFilename(), e);
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            // 通用异常兜底
            log.error("PDF上传未知异常，chatId：{}，文件名：{}", chatId, file.getOriginalFilename(), e);
            return Result.fail("上传文件失败！");
        }
    }


    @GetMapping("/file/{chatId}")
    public ResponseEntity<Resource> download(@PathVariable("chatId")
                                                 @NotBlank(message = "下载PDF的会话ID不能为空")
                                                 String chatId) throws IOException {
        // 1.读取文件
        try {
            // 核心文件读取逻辑包trycatch
            Resource resource = fileService.getFile(chatId);
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            if (!resource.isReadable()) {
                throw new BusinessException(403, "文件不可读，无访问权限");
            }
            String filename = URLEncoder.encode(Objects.requireNonNull(resource.getFilename()), StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (BusinessException e) {
            // 自定义异常（如文件不可读）直接抛，走全局处理器
            throw e;
        } catch (Exception e) {
            // 通用异常兜底
            log.error("PDF下载未知异常，chatId：{}", chatId, e);
            throw new BusinessException(500, "文件下载失败，请稍后重试");
        }
    }

}