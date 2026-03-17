package com.Storm.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.List;

// 实现ConstraintValidator接口，泛型：注解类型 + 被校验参数类型
public class ChatTypeValidator implements ConstraintValidator<ValidChatType, String> {
    // 合法的类型列表（从注解参数中获取）
    private List<String> allowedValues;

    // 初始化：获取注解上配置的allowedValues
    @Override
    public void initialize(ValidChatType constraintAnnotation) {
        this.allowedValues = Arrays.asList(constraintAnnotation.allowedValues());
    }

    // 核心：校验逻辑（判断type是否在合法列表中）
    @Override
    public boolean isValid(String type, ConstraintValidatorContext context) {
        // 非空校验交给@NotBlank，这里只校验合法性
        if (type == null || type.isBlank()) {
            return false;
        }
        return allowedValues.contains(type);
    }
}