package com.example.refund.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.web.client.RestClient;

@Configuration
public class ChatConfig {
    @Bean
    @Scope("prototype")
    RestClient.Builder restClientBuilder() {
        // 该版本 DashScope 自动配置还需要同步 HTTP 客户端；纯 WebFlux 不自动提供它。
        return RestClient.builder();
    }

    @Bean
    ChatMemory chatMemory() {
        // Spring AI 1.0.0 在记忆对象上配置消息窗口，而不是请求级 retrieve_size。
        return MessageWindowChatMemory.builder().maxMessages(100).build();
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory,
            @Value("classpath:prompts/refund-system.txt") Resource prompt) throws IOException {
        return ChatClient.builder(chatModel)
                .defaultSystem(prompt.getContentAsString(StandardCharsets.UTF_8))
                .defaultAdvisors(new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
