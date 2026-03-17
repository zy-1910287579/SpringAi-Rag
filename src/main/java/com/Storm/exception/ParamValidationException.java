// 1. 参数校验异常（扩展BusinessException）
package com.Storm.exception;

public class ParamValidationException extends BusinessException {
    // 构造方法1：仅传提示语（错误码默认400）
    public ParamValidationException(String message) {
        super(400, message);
    }
    // 构造方法2：自定义错误码+提示语（灵活扩展）
    public ParamValidationException(int code, String message) {
        super(code, message);
    }
}
