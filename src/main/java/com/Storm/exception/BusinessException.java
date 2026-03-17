package com.Storm.exception;

//自定义异常类必须要基础父类Exception或继承RuntimeException(不用必须throws)
//自定义异常类要单独写出来
public class BusinessException  extends RuntimeException{

    //异常类家族的属性一般就状态码和提示信息
    // 异常状态码
    private Integer code;
/*---------------------------------------------------------------------------------------*/
    // 构造方法,灵活定制版,可以手动指定错误码!!!
    public BusinessException(Integer code, String message) {
        // 调用父类构造方法,父类会自动将message赋给this.message
        super(message);
        this.code = code;
    }
    // 构造函数，不指定错误码，默认400,请求参数错误!!!
    public BusinessException(String message) {
        this(400, message);
    }
/*---------------------------------------------------------------------------------------*/
    // get/set（Lombok也能加，这里手动写方便新手理解）
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }
}
