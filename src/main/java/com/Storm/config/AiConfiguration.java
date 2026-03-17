package com.Storm.config;

/**配置类目前缺少 Bean 初始化阶段的异常处理——Bean 的创建发生在项目启动时，
 // 如果初始化失败（比如依赖缺失、配置错误），会导致项目启动崩溃，且报错信息杂乱（原生 Spring 异常），
 // 无法快速定位问题。以下是需要优化的点，且完全贴合自定义异常体系：**/
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.context.annotation.Configuration;
import com.Storm.constants.SystemConstants;
import com.Storm.exception.BusinessException;
import com.Storm.exception.ThirdPartyApiException;
import com.Storm.tools.CourseTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;

import javax.swing.*;

/**
 * AI核心配置类（Bean初始化异常处理优化版）
 * 核心：启动阶段捕获Bean初始化异常，用自定义异常包装，便于快速定位问题
 */
/*Bean 初始化失败是「项目启动阶段」的致命错误，必须记录完整堆栈，方便排查!!*/
@Slf4j
@Configuration
public class AiConfiguration {

    @Bean
    public ChatMemory chatMemory() {
        try {
            return new InMemoryChatMemory();
        } catch (Exception e) {
            log.error("ChatMemory（会话内存）初始化失败", e);
            // 抛业务异常，明确启动失败原因（500：系统级初始化错误）//这里的getMessage是父类RuntimeException的getMessage方法
            throw new BusinessException(500, "会话内存初始化失败：" + e.getMessage());
        }
    }



    /*
    Spring 中 Bean 名称是唯一的（默认等于方法名），
    即使多个 Bean 类型相同（都是 DefaultChatClient），也能通过名称精准定位；
    */

    /*重要知识点*/
    /**这里的自定义bean我好好解释一下,首先,方法的返回值是一个对象,这个对象就是纳入Spring容器的 Bean，
    所以方法的类型要和返回值的类型一致。
    而方法名就是 Bean 的名称，Spring 容器会根据方法名自动生成 Bean 的名称，
    在执行这个方法的时候,会利用这个对象的构造函数啊,set方法啊给这个对象设置各种参数,
    如果是builder工厂,本质也是一样是在设置参数*/
    /*======================================================================*/

    /*@Bean 方法的参数「默认会优先从 Spring 容器中找对应的 Bean 注入」，
    但参数不一定 “必须是其他 Bean” —— 绝大多数场景下是（这是主流,日常开发 90%+ 的场景）*/
    @Bean
    public ChatClient chatClient(OpenAiChatModel model, ChatMemory chatMemory){
        // 1. 非空校验：核心依赖缺失直接抛异常（启动阶段提前暴露问题）
        if (model == null) {
            log.error("OpenAiChatModel Bean未找到，请检查OpenAI配置（api-key是否正确）");
            throw new ThirdPartyApiException("OpenAI大模型客户端初始化失败：核心模型Bean缺失");
        }
        if (chatMemory == null) {
            log.error("ChatMemory Bean未找到");
            throw new BusinessException(500, "会话内存Bean缺失，无法创建通用ChatClient");
        }
        //开始初始化
        try {
            return ChatClient.builder(model)
                    .defaultAdvisors(new SimpleLoggerAdvisor(), new MessageChatMemoryAdvisor(chatMemory))
                    .build();
        } catch (Exception e) {
            log.error("通用ChatClient初始化失败", e);
            throw new BusinessException(500, "通用AI问答客户端初始化失败：" + e.getMessage());
        }

    }

    @Bean
    public ChatClient gameChatClient(OpenAiChatModel model,ChatMemory chatMemory){
        // 非空校验（和通用ChatClient一致，保证核心依赖）
        if (model == null) {
            throw new ThirdPartyApiException("OpenAI大模型客户端初始化失败：核心模型Bean缺失");
        }
        if (chatMemory == null) {
            throw new BusinessException(500, "会话内存Bean缺失，无法创建游戏（角色扮演）ChatClient");
        }
        try {
            return ChatClient.builder(model)
                    .defaultSystem(SystemConstants.GAME_SYSTEM_PROMPT)
                    .defaultAdvisors(new SimpleLoggerAdvisor(), new MessageChatMemoryAdvisor(chatMemory))
                    .build();
        } catch (Exception e) {
            log.error("游戏（角色扮演）ChatClient初始化失败", e);
            throw new BusinessException(500, "角色扮演AI客户端初始化失败：" + e.getMessage());
        }
    }

    @Bean
    public ChatClient serviceChatClient(OpenAiChatModel model, ChatMemory chatMemory, CourseTools courseTools){
        // 非空校验：新增CourseTools（FunctionCalling工具）的校验
        if (model == null) {
            throw new ThirdPartyApiException("OpenAI大模型客户端初始化失败：核心模型Bean缺失");
        }
        if (chatMemory == null) {
            throw new BusinessException(500, "会话内存Bean缺失，无法创建客服ChatClient");
        }
        if (courseTools == null) {
            log.error("CourseTools Bean未找到，请检查FunctionCalling工具类配置");
            throw new BusinessException(500, "FunctionCalling工具类Bean缺失，无法创建智能客服ChatClient");
        }

        try {
            return ChatClient.builder(model)
                    .defaultSystem(SystemConstants.SERVICE_SYSTEM_PROMPT)
                    .defaultAdvisors(new SimpleLoggerAdvisor(), new MessageChatMemoryAdvisor(chatMemory))
                    .defaultTools(courseTools)
                    .build();
        } catch (Exception e) {
            log.error("智能客服ChatClient初始化失败", e);
            throw new BusinessException(500, "智能客服AI客户端初始化失败：" + e.getMessage());
        }
    }

    @Bean
    public VectorStore vectorStore(OpenAiEmbeddingModel embeddingModel){
        // 非空校验：向量模型是RAG/PDF问答的核心依赖
        if (embeddingModel == null) {
            log.error("OpenAiEmbeddingModel Bean未找到，请检查OpenAI Embedding配置");
            throw new ThirdPartyApiException("OpenAI向量模型初始化失败：核心Embedding Bean缺失");
        }

        try {
            return SimpleVectorStore.builder(embeddingModel).build();
        } catch (Exception e) {
            log.error("VectorStore（本地向量库）初始化失败", e);
            throw new BusinessException(500, "本地向量库初始化失败（RAG核心依赖）：" + e.getMessage());
        }
    }


    /*openAiChatModel Bean 不是你手动写 @Bean 定义的，而是 Spring AI 自动配置 基于你 Yaml 里的配置创建的；
    自动配置的触发条件：引入 Spring AI OpenAI 依赖 + Yaml 配置 spring.ai.openai.api-key；
    只有需要自定义模型参数（如 GPT-4、不同温度）时，才需要手动定义 OpenAiChatModel 的 @Bean。*/
    @Bean
    public ChatClient pdfChatClient(OpenAiChatModel model,ChatMemory chatMemory, VectorStore vectorStore){

        // 非空校验：PDF问答依赖向量库，必须校验
        if (model == null) {
            throw new ThirdPartyApiException("OpenAI大模型客户端初始化失败：核心模型Bean缺失");
        }
        if (chatMemory == null) {
            throw new BusinessException(500, "会话内存Bean缺失，无法创建PDF问答ChatClient");
        }
        if (vectorStore == null) {
            log.error("VectorStore Bean未找到，请检查本地向量库配置");
            throw new BusinessException(500, "本地向量库Bean缺失，无法创建PDF问答AI客户端");
        }
       /*虽然方法声明返回的是 ChatClient 接口，
        但实际被 Spring 容器纳入 Bean 管理的是 DefaultChatClient 这个具体实现类的实例。*/
        try {
            return ChatClient.builder(model)
                    .defaultSystem("""
                            你是专业的行业文档问答助手，仅基于上传的PDF文档内容回答问题：
                            1. 严格遵循文档中的原文信息作答，文档未提及的内容一律不得编造、推断或扩展；
                            2. 若问题超出文档覆盖范围，直接回复：「当前文档中无相关内容，无法解答该问题」；
                            3. 回答需精准对应文档中的章节/条款（如有），比如「根据文档第X部分内容：XXX」；
                            4. 对于数字、专业术语、流程步骤等，必须与文档完全一致，禁止修改或简化。""")
                    .defaultAdvisors(
                            new SimpleLoggerAdvisor(),
                            new MessageChatMemoryAdvisor(chatMemory),
                            new QuestionAnswerAdvisor(
                                    vectorStore, SearchRequest.builder()
                                    .similarityThreshold(0.3)
                                    .topK(5)
                                    .build()
                            )
                    )
                    .build();
        } catch (Exception e) {
            log.error("PDF问答ChatClient初始化失败", e);
            throw new BusinessException(500, "PDF文档问答AI客户端初始化失败：" + e.getMessage());
        }
    }
}
