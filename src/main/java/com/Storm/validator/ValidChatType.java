package com.Storm.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;


/*!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 ✅ 引入 validation 依赖 = 拿到了 “通用参数校验工具包”（内置注解）；
 ✅ 写 ValidChatType+ChatTypeValidator = 给这个工具包加 “业务专属插件”；
 ✅ 所有校验最终都走全局异常处理器 = 不管用基础工具还是自定义插件，最终都按统一规则返回结果。*/

// 声明这是一个校验注解，指定校验逻辑实现类
@Constraint(validatedBy = ChatTypeValidator.class)
// 可加在字段/方法参数上
@Target({ElementType.FIELD, ElementType.PARAMETER})
// 运行时生效
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ValidChatType {
    // 校验失败的提示语（可自定义）
    String message() default "会话类型不合法（支持：chat/service/pdf/game）";
    // 分组校验（实习生暂时用不到，必填）
    Class<?>[] groups() default {};
    // 负载（实习生暂时用不到，必填）
    Class<? extends Payload>[] payload() default {};
    // 扩展：允许指定合法的type值（比如PDF模块限定只能是pdf）
    String[] allowedValues() default {"chat", "service", "pdf", "game"};
}