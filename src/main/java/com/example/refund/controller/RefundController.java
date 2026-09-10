package com.example.refund.controller;

import java.util.UUID;

import com.example.refund.model.ChatStatus;
import com.example.refund.model.OrderChat;
import com.example.refund.tools.OrderTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@RestController
@RequestMapping("/api/refund")
public class RefundController {
    private static final Logger log = LoggerFactory.getLogger(RefundController.class);
    private final ChatClient chatClient;
    private final OrderTools orderTools;

    public RefundController(ChatClient chatClient, OrderTools orderTools) {
        this.chatClient = chatClient;
        this.orderTools = orderTools;
    }

    @GetMapping("/newChat")
    public Mono<OrderChat> newChat(@RequestParam String userId, @RequestParam String orderId) {
        String chatId = UUID.randomUUID().toString();
        // 按作者思路：初始化也调用模型，由 entity() 将输出转换为 OrderChat。
        // call() 是阻塞调用，WebFlux 中移至 boundedElastic 执行。
        return Mono.fromCallable(() -> this.chatClient.prompt()
                .user(String.format(
                        "我要咨询订单相关的售后问题，我的用户id是%s,我的订单号是: %s，"
                        + "本地的对话Id是 %s，当前状态是 %s",
                        userId, orderId, chatId, ChatStatus.CHAT_START.name()))
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId))
                .call()
                .entity(OrderChat.class))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> ask(@RequestParam String question, @RequestParam String chatId) {
        return this.chatClient.prompt()
                .user(question)
                .tools(orderTools) // 提供工具，不直接决定是否退款；由模型选择调用。
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId))
                .stream()
                .content()
                .map(text -> ServerSentEvent.builder(text).event("message").build())
                .concatWithValues(ServerSentEvent.builder("完成").event("done").build())
                .doOnError(error -> log.error("模型流式调用失败", error))
                .onErrorResume(error -> Flux.just(ServerSentEvent.builder(
                        "模型请求失败，请检查服务端日志、API Key 和模型配置。")
                        .event("failure").build()));
    }
}
