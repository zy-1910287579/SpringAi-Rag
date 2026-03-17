// 4. 文件处理异常（PDF解析为主）
package com.Storm.exception;

public class FileProcessException extends BusinessException {
    public FileProcessException(String message) {
        super(500, message);
    }
    public FileProcessException(String message, Throwable cause) {
        super(500, message);
        this.initCause(cause);
    }
}