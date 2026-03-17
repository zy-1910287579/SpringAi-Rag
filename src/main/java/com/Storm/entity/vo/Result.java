package com.Storm.entity.vo;

import lombok.Data;

//标准返回结果
@Data
public class Result<T> {
    //状态码
    private Integer code;
    //提示信息
    private String msg;
    //数据
    private T data;

    // 静态工具方法：简化调用（实习项目常用写法）
    // 成功（带数据）
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("操作成功");
        result.setData(data);
        return result;
    }

    // 成功（无数据）
    public static <T> Result<T> success() {
        return success(null);
    }

    // 失败（自定义状态码+提示）
    public static <T> Result<T> fail(Integer code, String msg) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(null);
        return result;
    }

    // 失败（默认500系统异常）
    public static <T> Result<T> fail(String msg) {
        return fail(500, msg);
    }
}