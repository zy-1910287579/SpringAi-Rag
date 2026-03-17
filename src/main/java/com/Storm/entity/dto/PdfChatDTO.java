package com.Storm.entity.dto;

import com.Storm.validator.ValidChatType;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Lombok：自动生成get/set/toString
@Data
public class PdfChatDTO {
    // 内置注解：非空校验
    @NotBlank(message = "提问内容不能为空")
    private String prompt;

    // 内置注解：非空校验
    @NotBlank(message = "会话ID不能为空")
    private String chatId;

    // 自定义注解：type只能是pdf
    @ValidChatType(allowedValues = {"pdf"}, message = "PDF模块会话类型只能是pdf")
    private String type;

}