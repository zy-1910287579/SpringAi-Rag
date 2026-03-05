package com.Storm.config;



import com.Storm.constants.SystemConstants;
import com.Storm.tools.CourseTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;



@Configuration
public class AiConfiguration {

    @Bean
    public ChatMemory chatMemory(){
        return new InMemoryChatMemory();
    }


    //Spring 中 Bean 名称是唯一的（默认等于方法名），
    // 即使多个 Bean 类型相同（都是 DefaultChatClient），也能通过名称精准定位；
    @Bean
    public ChatClient chatClient(OpenAiChatModel model, ChatMemory chatMemory){
        return ChatClient
                .builder(model)
                .defaultAdvisors(new SimpleLoggerAdvisor(), new MessageChatMemoryAdvisor(chatMemory))
                .build();
    }

    @Bean
    public ChatClient gameChatClient(OpenAiChatModel model,ChatMemory chatMemory){
        return ChatClient
                .builder(model)
                .defaultSystem(SystemConstants.GAME_SYSTEM_PROMPT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new MessageChatMemoryAdvisor(chatMemory)
                )
                .build();
    }

    @Bean
    public ChatClient serviceChatClient(OpenAiChatModel model, ChatMemory chatMemory, CourseTools courseTools){
        return ChatClient
                .builder(model)
                .defaultSystem(SystemConstants.SERVICE_SYSTEM_PROMPT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new MessageChatMemoryAdvisor(chatMemory)
                )
                .defaultTools(courseTools)
                .build();
    }

    @Bean
    public VectorStore vectorStore(OpenAiEmbeddingModel embeddingModel){
        return SimpleVectorStore.builder(embeddingModel)
                .build();
    }


    //OpenAiChatModel Bean 不是你手动写 @Bean 定义的，而是 Spring AI 自动配置 基于你 Yaml 里的配置创建的；
    //自动配置的触发条件：引入 Spring AI OpenAI 依赖 + Yaml 配置 spring.ai.openai.api-key；
    //只有需要自定义模型参数（如 GPT-4、不同温度）时，才需要手动定义 OpenAiChatModel 的 @Bean。
    @Bean
    public ChatClient pdfChatClient(OpenAiChatModel model,ChatMemory chatMemory, VectorStore vectorStore){
//        虽然方法声明返回的是 ChatClient 接口，
//        但实际被 Spring 容器纳入 Bean 管理的是 DefaultChatClient 这个具体实现类的实例。
        return ChatClient
                .builder(model)
                .defaultSystem("请根据上下文回答问题,上下文没有的问题,不要随便编造")
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new MessageChatMemoryAdvisor(chatMemory),
                        new QuestionAnswerAdvisor
                                (  vectorStore,SearchRequest.builder()

                                .similarityThreshold(0.3)
                                .topK(5)
                                .build()
                                )
                )
                .build();
    }
}
