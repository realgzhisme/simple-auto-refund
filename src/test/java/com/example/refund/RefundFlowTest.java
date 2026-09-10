package com.example.refund;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import com.example.refund.config.ChatConfig;
import com.example.refund.controller.RefundController;
import com.example.refund.model.OrderChat;
import com.example.refund.service.OrderManageService;
import com.example.refund.tools.OrderTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 使用可控模型响应检查应用接线，不据此判断真实模型的意图识别能力。 */
class RefundFlowTest {
    private ChatModel model;
    private ChatMemory memory;
    private RefundController controller;
    private OrderManageService service;
    private OrderTools tools;

    @BeforeEach
    void setUp() throws Exception {
        model = mock(ChatModel.class);
        memory = MessageWindowChatMemory.builder().maxMessages(100).build();
        service = mock(OrderManageService.class);
        tools = new OrderTools(service);
        ChatClient client = new ChatConfig().chatClient(model, memory,
                new ClassPathResource("prompts/refund-system.txt"));
        controller = new RefundController(client, tools);

        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            var matcher = Pattern.compile("本地的对话Id是 ([a-f0-9-]+)")
                    .matcher(prompt.getUserMessage().getText());
            assertThat(matcher.find()).isTrue();
            return response("""
                    {"orderId":"202609100001","userId":"1001","chatId":"%s","status":"CHAT_START"}
                    """.formatted(matcher.group(1)));
        });
    }

    @Test
    void initializationUsesModelStructuredOutputAndStoresContext() {
        WebTestClient.bindToController(controller).build().get()
                .uri("/api/refund/newChat?userId=1001&orderId=202609100001")
                .exchange().expectStatus().isOk().expectBody(OrderChat.class)
                .value(chat -> {
                    assertThat(chat.orderId()).isEqualTo("202609100001");
                    assertThat(chat.chatId()).matches("[a-f0-9-]{36}");
                    assertThat(memory.get(chat.chatId())).anySatisfy(message ->
                            assertThat(message.getText()).contains("202609100001", "1001"));
                });
        verify(model).call(any(Prompt.class));
        verifyNoInteractions(service);
    }

    @Test
    void followUpReceivesHistoryAndDifferentSessionDoesNot() {
        OrderChat chat = controller.newChat("1001", "202609100001").block();
        List<Prompt> prompts = new CopyOnWriteArrayList<>();
        when(model.stream(any(Prompt.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0));
            return Flux.just(response("请确认"), response("袖口是否开线？"));
        });

        StepVerifier.create(controller.ask("我的衬衫袖口开线了", chat.chatId()))
                .assertNext(event -> assertThat(event.data()).isEqualTo("请确认"))
                .assertNext(event -> assertThat(event.data()).isEqualTo("袖口是否开线？"))
                .assertNext(event -> assertThat(event.event()).isEqualTo("done"))
                .verifyComplete();
        controller.ask("对的", chat.chatId()).collectList().block();
        assertThat(texts(prompts.get(1))).contains("202609100001", "我的衬衫袖口开线了", "对的");

        controller.ask("物流到哪了", "another-session").collectList().block();
        assertThat(texts(prompts.get(2))).doesNotContain("202609100001", "我的衬衫袖口开线了");
        verifyNoInteractions(service); // 注册工具或普通回复不会直接触发退款。
    }

    @Test
    void annotatedToolMapsModelArgumentsToBusinessMethod() {
        var callbacks = ToolCallbacks.from(tools);
        assertThat(callbacks).hasSize(1);
        assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("apply_refund");
        when(service.refund("202609100001", "袖口开线")).thenReturn("mock-request-id");
        String result = callbacks[0].call("""
                {"orderId":"202609100001","name":"衬衫","reason":"袖口开线"}
                """);
        verify(service).refund("202609100001", "袖口开线");
        assertThat(result).contains("已为商品", "衬衫", "202609100001", "袖口开线");
    }

    @Test
    void streamFailureProducesAnExplicitFailureEvent() {
        when(model.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("test")));
        StepVerifier.create(controller.ask("你好", "failed-session"))
                .assertNext(event -> assertThat(event.event()).isEqualTo("failure"))
                .verifyComplete();
    }

    @Test
    void httpAskUsesSseAndRequiresConversationId() {
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(response("您好")));
        var client = WebTestClient.bindToController(controller).build();
        client.get().uri("/api/refund/ask?question=hello&chatId=http-session")
                .exchange().expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class).value(body ->
                        assertThat(body).contains("event:message", "data:您好", "event:done"));
        client.get().uri("/api/refund/ask?question=hello")
                .exchange().expectStatus().isBadRequest();
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static String texts(Prompt prompt) {
        return prompt.getInstructions().stream().map(Message::getText)
                .reduce("", (left, right) -> left + "\n" + right);
    }
}
