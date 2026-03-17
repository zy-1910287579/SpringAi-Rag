package com.Storm.handler;

import com.Storm.entity.vo.Result;
import com.Storm.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;


/*实际开发中的最佳实践（总结）
先做前置校验：用 if 判断所有「可预见的业务错误场景」，主动抛带精准错误码的 BusinessException；
再靠全局兜底：全局异常处理器捕获「不可预见的系统异常」，统一返回 500 错误，同时记录详细日志；
异常提示分层：
业务异常（4xx）：提示用户能看懂的信息（如 “会话 ID 不能为空”）；
系统异常（5xx）：提示 “服务器错误，请联系管理员”，日志里记录详细异常栈。*/

//核心注解:@RestControllerAdvice
// 不是只能处理 Controller 层 “自己写的异常” ——
//它能处理「所有未被手动 catch、最终冒泡到 Spring MVC 请求处理链路中的异常」；
// Service/Dao 等其他层的异常，只要没被本地 catch，会自动向上传递到 Controller 层，
// 最终被全局异常处理器捕获。
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    //必须先处理自定义业务异常（优先级最高，面试会问异常处理优先级：自定义异常 > 通用异常>其他异常）

   // 当项目中抛出 BusinessException 类型的异常时，
   // 就用当前这个方法来处理，别用默认的系统处理逻辑”
   //@ExceptionHandler就是表示具体的处理逻辑


    // ========== 新增1：处理@Valid + @RequestBody（JSON参数）的校验异常 ==========
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        // 提取校验失败的字段+提示信息（比如“type：会话类型不合法”）
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + "：" + fieldError.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        log.info("JSON参数校验异常：{}", errorMsg);
        // 复用你的ParamValidationException的400错误码，保持异常体系统一
        return Result.fail(400, errorMsg);
    }

    // ========== 新增2：处理@Validated + @RequestParam/@PathVariable（非JSON参数）的校验异常 ==========
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) {
        // 提取校验失败的提示信息（比如“会话类型不合法（支持：chat/service/pdf/game）”）
        String errorMsg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.joining("；"));
        log.info("URL/路径参数校验异常：{}", errorMsg);
        // 同样返回400错误码，和ParamValidationException保持一致
        return Result.fail(400, errorMsg);
    }


    // 新增：处理参数校验异常
    @ExceptionHandler(ParamValidationException.class)
    public Result<?> handleParamValidationException(ParamValidationException e) {
        log.info("参数校验异常：{}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    // 新增：处理资源不存在异常
    @ExceptionHandler(ResourceNotFoundException.class)
    public Result<?> handleResourceNotFoundException(ResourceNotFoundException e) {
        log.info("资源不存在异常：{}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    // 新增：处理第三方API调用异常
    @ExceptionHandler(ThirdPartyApiException.class)
    public Result<?> handleThirdPartyApiException(ThirdPartyApiException e) {
        log.error("第三方API调用失败", e); // 记录原始栈
        return Result.fail(e.getCode(), e.getMessage());
    }

    // 新增：处理文件处理异常
    @ExceptionHandler(FileProcessException.class)
    public Result<?> handleFileProcessException(FileProcessException e) {
        log.error("文件处理（PDF解析）失败", e);
        return Result.fail(e.getCode(), e.getMessage());
    }
    //1.自定义父类业务异常------------------------------------------------------------'
    //语法上不是 “必须”，但在你的项目中，
    // 继承BusinessException是「合理且最优」的选择
    // （面试时这么回答，能体现你懂 “面向对象设计” 和 “企业级异常规范”）。
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        /*封装自定义异常的状态码和提示
        错误的情况下,只需要错误码和错误信息
        业务异常：记录info级别日志（非错误，是用户操作问题）*/
        log.info("业务异常：{}，错误码：{}", e.getMessage(), e.getCode());
        //这里在抛出异常的时候new BusinessException(xxx,xxx)，已经初始化了错误码和错误信息，所以这里直接返回即可!!!
        return Result.fail(e.getCode(), e.getMessage());
    }

    //2.系统异常------------------------------------------------------------
    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntimeException(RuntimeException e) {
        // 系统异常：记录error级别日志，包含完整堆栈（排查问题用）
        log.error("运行时系统异常", e);
        // 对外脱敏：不返回e.getMessage()（避免暴露系统细节，比如“空指针异常”）
        return Result.fail(500, "系统临时异常，请稍后重试");
    }

    // 3. 处理所有其他异常（兜底，防止漏拦截)---------------------------------------
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        // 兜底异常：记录error级别日志，包含完整堆栈
        log.error("服务器内部异常", e);
        // 对外返回最通用的提示（完全脱敏）
        return Result.fail(500, "服务器内部错误，请联系管理员");
    }
}
