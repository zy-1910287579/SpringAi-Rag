// 3. 第三方API调用异常
package com.Storm.exception;

public class ThirdPartyApiException extends BusinessException {
    public ThirdPartyApiException(String message) {
        super(502, message);
    }
    // 重载：携带异常原因（日志排查用）
    public ThirdPartyApiException(String message, Throwable cause) {
        super(502, message);
        this.initCause(cause); // 保留第三方调用的原始异常栈
    }
}
