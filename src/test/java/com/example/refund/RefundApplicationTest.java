package com.example.refund;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.dashscope.api-key=local-test-not-a-real-key")
class RefundApplicationTest {
    @Autowired
    private WebTestClient client;

    @Test
    void actualApplicationStartsAndServesChatPageWithoutCallingProvider() {
        client.get().uri("/").exchange().expectStatus().isOk()
                .expectBody(String.class).value(body ->
                        assertThat(body).contains("售后客服", "开始新会话", "new EventSource"));
    }
}
