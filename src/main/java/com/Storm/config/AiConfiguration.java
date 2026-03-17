package com.Storm.config;

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
            InMemoryChatMemory chatMemory = new InMemoryChatMemory();
            log.info("ChatMemory（会话内存）初始化成功"); // 新增：成功日志，便于启动排查
            return chatMemory;
        } catch (Exception e) {
            log.error("【Bean初始化失败】ChatMemory（会话内存）初始化失败", e); // 优化：日志前缀统一，定位更准
            // 异常信息补充关键提示（如“请检查内存配置/依赖”）
            throw new BusinessException(500, "会话内存初始化失败：" + e.getMessage() + "，请检查Spring AI内存依赖是否正常");
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
            String errorMsg = "OpenAiChatModel Bean未找到，请检查OpenAI配置（api-key是否正确、spring.ai.openai依赖是否引入）";
            log.error("【Bean初始化校验失败】{}", errorMsg); // 优化：日志前缀统一
            throw new ThirdPartyApiException("OpenAI大模型客户端初始化失败：核心模型Bean缺失");
        }
        if (chatMemory == null) {
            String errorMsg = "ChatMemory Bean未找到，无法创建通用ChatClient";
            log.error("【Bean初始化校验失败】{}", errorMsg);
            throw new BusinessException(500, "会话内存Bean缺失，无法创建通用ChatClient");
        }
        //开始初始化
        try {
            ChatClient chatClient = ChatClient.builder(model)
                    .defaultAdvisors(new SimpleLoggerAdvisor(), new MessageChatMemoryAdvisor(chatMemory))
                    .build();
            log.info("通用ChatClient（AI问答客户端）初始化成功"); // 新增：成功日志
            return chatClient;
        } catch (Exception e) {
            log.error("【Bean初始化失败】通用ChatClient初始化失败", e);
            throw new BusinessException(500, "通用AI问答客户端初始化失败：" + e.getMessage());
        }

    }
    // ========== 优化3：游戏ChatClient Bean（统一日志格式，异常信息补充上下文） ==========
    @Bean
    public ChatClient gameChatClient(OpenAiChatModel model,ChatMemory chatMemory){
        // 非空校验（和通用ChatClient一致，保证核心依赖）
        if (model == null) {
            String errorMsg = "OpenAiChatModel Bean未找到，请检查OpenAI配置（api-key是否正确）";
            log.error("【Bean初始化校验失败】{}", errorMsg);
            throw new ThirdPartyApiException("OpenAI大模型客户端初始化失败：核心模型Bean缺失");
        }
        if (chatMemory == null) {
            String errorMsg = "ChatMemory Bean未找到，无法创建游戏（角色扮演）ChatClient";
            log.error("【Bean初始化校验失败】{}", errorMsg);
            throw new BusinessException(500, "会话内存Bean缺失，无法创建游戏（角色扮演）ChatClient");
        }
        try {
            ChatClient chatClient = ChatClient.builder(model)
                    .defaultSystem(SystemConstants.GAME_SYSTEM_PROMPT)
                    .defaultAdvisors(new SimpleLoggerAdvisor(), new MessageChatMemoryAdvisor(chatMemory))
                    .build();
            log.info("游戏ChatClient（角色扮演AI客户端）初始化成功"); // 新增：成功日志
            return chatClient;
        } catch (Exception e) {
            log.error("【Bean初始化失败】游戏ChatClient初始化失败", e);
            throw new BusinessException(500, "角色扮演AI客户端初始化失败：" + e.getMessage());
        }
    }

    @Bean
    public ChatClient serviceChatClient(OpenAiChatModel model, ChatMemory chatMemory, CourseTools courseTools){
        // 非空校验：新增CourseTools（FunctionCalling工具）的校验
        if (model == null) {
            String errorMsg = "OpenAiChatModel Bean未找到，请检查OpenAI配置（api-key是否正确）";
            log.error("【Bean初始化校验失败】{}", errorMsg);
            throw new ThirdPartyApiException("OpenAI大模型客户端初始化失败：核心模型Bean缺失");
        }
        if (chatMemory == null) {
            String errorMsg = "ChatMemory Bean未找到，无法创建客服ChatClient";
            log.error("【Bean初始化校验失败】{}", errorMsg);
            throw new BusinessException(500, "会话内存Bean缺失，无法创建客服ChatClient");
        }
        if (courseTools == null) {
            String errorMsg = "CourseTools Bean未找到，请检查FunctionCalling工具类配置（CourseTools是否加@Component注解）";
            log.error("【Bean初始化校验失败】{}", errorMsg);
            log.error("CourseTools Bean未找到，请检查FunctionCalling工具类配置");
            throw new BusinessException(500, "FunctionCalling工具类Bean缺失，无法创建智能客服ChatClient");
        }

        try {
            ChatClient chatClient = ChatClient.builder(model)
                    .defaultSystem(SystemConstants.SERVICE_SYSTEM_PROMPT)
                    .defaultAdvisors(new SimpleLoggerAdvisor(), new MessageChatMemoryAdvisor(chatMemory))
                    .defaultTools(courseTools)
                    .build();
            log.info("客服ChatClient（智能客服AI客户端）初始化成功"); // 新增：成功日志
            return chatClient;
        } catch (Exception e) {
            log.error("【Bean初始化失败】智能客服ChatClient初始化失败", e);
            throw new BusinessException(500, "智能客服AI客户端初始化失败：" + e.getMessage());
        }
    }

    // ========== 优化5：VectorStore Bean（补充成功日志，异常信息补充RAG相关提示） ==========
    @Bean
    public VectorStore vectorStore(OpenAiEmbeddingModel embeddingModel){
        // 非空校验：向量模型是RAG/PDF问答的核心依赖
        if (embeddingModel == null) {
            String errorMsg = "OpenAiEmbeddingModel Bean未找到，请检查OpenAI Embedding配置（api-key是否正确、embedding模型是否启用）";
            log.error("【Bean初始化校验失败】{}", errorMsg);
            throw new ThirdPartyApiException("OpenAI向量模型初始化失败：核心Embedding Bean缺失");
        }

        try {
            VectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
            log.info("VectorStore（本地向量库）初始化成功（RAG核心依赖）"); // 新增：成功日志，标注RAG关联
            return vectorStore;
        } catch (Exception e) {
            log.error("【Bean初始化失败】VectorStore（本地向量库）初始化失败", e);
            throw new BusinessException(500, "本地向量库初始化失败（RAG核心依赖）：" + e.getMessage());
        }
    }

    // ========== 优化6：PDF ChatClient Bean（补充SearchRequest参数校验，统一日志格式） ==========
    /*openAiChatModel Bean 不是你手动写 @Bean 定义的，而是 Spring AI 自动配置 基于你 Yaml 里的配置创建的；
    自动配置的触发条件：引入 Spring AI OpenAI 依赖 + Yaml 配置 spring.ai.openai.api-key；
    只有需要自定义模型参数（如 GPT-4、不同温度）时，才需要手动定义 OpenAiChatModel 的 @Bean。*/
    @Bean
    public ChatClient pdfChatClient(OpenAiChatModel model,ChatMemory chatMemory, VectorStore vectorStore){

        // 非空校验：PDF问答依赖向量库，必须校验
        if (model == null) {
            String errorMsg = "OpenAiChatModel Bean未找到，请检查OpenAI配置（api-key是否正确）";
            log.error("【Bean初始化校验失败】{}", errorMsg);
            throw new ThirdPartyApiException("OpenAI大模型客户端初始化失败：核心模型Bean缺失");
        }
        if (chatMemory == null) {
            String errorMsg = "ChatMemory Bean未找到，无法创建PDF问答ChatClient";
            log.error("【Bean初始化校验失败】{}", errorMsg);
            throw new BusinessException(500, "会话内存Bean缺失，无法创建PDF问答ChatClient");
        }
        if (vectorStore == null) {
            log.error("VectorStore Bean未找到，请检查本地向量库配置");
            throw new BusinessException(500, "本地向量库Bean缺失，无法创建PDF问答AI客户端");
        }
       /*虽然方法声明返回的是 ChatClient 接口，
        但实际被 Spring 容器纳入 Bean 管理的是 DefaultChatClient 这个具体实现类的实例。*/
        try {
            // 新增：SearchRequest参数合法性校验（避免阈值/topK配置错误导致初始化失败）
            SearchRequest searchRequest = SearchRequest.builder()
                    .similarityThreshold(0.3)
                    .topK(5)
                    .build();
            // 校验阈值和topK的合法范围
            if (searchRequest.getSimilarityThreshold() < 0 || searchRequest.getSimilarityThreshold() > 1) {
                throw new IllegalArgumentException("SimilarityThreshold（相似度阈值）必须在0-1之间，当前值：" + searchRequest.getSimilarityThreshold());
            }
            if (searchRequest.getTopK() < 1 || searchRequest.getTopK() > 100) {
                throw new IllegalArgumentException("TopK（召回数量）必须在1-100之间，当前值：" + searchRequest.getTopK());
            }

            ChatClient chatClient = ChatClient.builder(model)
                    .defaultSystem("""
                            你是专业的行业文档问答助手，仅基于上传的PDF文档内容回答问题：
                            1. 严格遵循文档中的原文信息作答，文档未提及的内容一律不得编造、推断或扩展；
                            2. 若问题超出文档覆盖范围，直接回复：「当前文档中无相关内容，无法解答该问题」；
                            3. 回答需精准对应文档中的章节/条款（如有），比如「根据文档第X部分内容：XXX」；
                            4. 对于数字、专业术语、流程步骤等，必须与文档完全一致，禁止修改或简化。""")
                    .defaultAdvisors(
                            new SimpleLoggerAdvisor(),
                            new MessageChatMemoryAdvisor(chatMemory),
                            new QuestionAnswerAdvisor(vectorStore, searchRequest)
                    )
                    .build();
            log.info("PDFChatClient（PDF文档问答AI客户端）初始化成功"); // 新增：成功日志
            return chatClient;
        } catch (IllegalArgumentException e) {
            // 优化：精准捕获参数非法异常，单独处理（便于定位配置错误）
            log.error("【Bean初始化参数错误】PDFChatClient初始化失败 - 参数非法：{}", e.getMessage());
            throw new BusinessException(500, "PDF文档问答AI客户端初始化失败（参数错误）：" + e.getMessage());
        } catch (Exception e) {
            log.error("【Bean初始化失败】PDFChatClient初始化失败", e);
            throw new BusinessException(500, "PDF文档问答AI客户端初始化失败：" + e.getMessage() + "，请检查向量库/QuestionAnswerAdvisor配置是否正常");
        }
    }
}
