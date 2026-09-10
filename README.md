# simple-auto-refund

按文章思路实现的 Spring AI 自动退款客服学习项目：模型识别质量问题，通过多轮确认后自主调用退款工具；普通问题继续对话。退款仅记录日志并生成 UUID，没有真实订单或支付操作。

## 技术栈

- JDK 21
- Spring Boot 3.4.5 / Spring AI 1.0.0
- Spring AI Alibaba 1.0.0.2，使用 DashScope ChatModel
- Spring WebFlux、SSE、内存对话记忆
- Maven Wrapper 3.9.9

这里固定的是适合复现文章 API 风格的依赖组合，不宣称是作者原工程版本或最新版本。

## 启动

在项目根目录运行，先在终端或 IDE Run Configuration 中设置环境变量：

```bash
export DASHSCOPE_API_KEY='你的百炼 API Key'
export DASHSCOPE_MODEL='qwen-plus'
./mvnw spring-boot:run
```

Windows 使用 `mvnw.cmd`。首次运行 Wrapper 需要网络下载 Maven 和依赖。

打开 http://localhost:8080 。默认只监听本机地址；端口可通过 `SERVER_PORT` 修改。

`.env.example` 仅用于说明变量名；Spring Boot **不会自动加载 `.env`**。不要把真实 Key 写入源码、配置模板、聊天或提交记录。未配置 Key 时应用无法完成启动。

## 体验步骤

1. 填写示例用户 ID `1001`、订单号 `202609100001`，点击“开始新会话”。这一步按作者思路真实调用模型，将其输出转换成 `OrderChat`。
2. 输入“我买的这件衬衫刚收到，袖口就开线了”。模型应先询问确认。
3. 回复“对的，刚收到就开线了，根本没法穿”。观察回复和后端的 `[模拟退款成功]` 日志。
4. 新建会话，输入“物流太慢了”。模型应安抚用户，不应触发退款。

页面最初的问候语是前端固定欢迎语；后续回复来自模型。是否选择工具由模型决定，没有写 `if (包含质量关键词) refund()`。真实模型结果有不确定性，请同时查看日志确认工具是否执行。

## 请求与代码对应关系

```text
GET /api/refund/newChat?userId=1001&orderId=202609100001
  → UUID 生成 chatId
  → 初始用户/订单信息进入提示词
  → MessageChatMemoryAdvisor 按 chatId 记录上下文
  → call().entity(OrderChat.class) 返回结构化对象

GET /api/refund/ask?chatId=上一步返回值&question=问题
  → 加载历史 + 本轮问题 + OrderTools 定义
  → 模型选择回答或请求 apply_refund
  → Spring AI 执行 OrderTools.refund
  → OrderManageService.refund 模拟退款
  → 模型根据工具文本结果继续回复
  → SSE 增量返回页面
```

- `config/ChatConfig.java`：系统提示词、日志 Advisor、100 条消息的内存窗口。
- `controller/RefundController.java`：保留作者的 newChat / ask 两段流程。
- `model/OrderChat.java`：结构化输出 record；`ChatStatus` 仅包含初始状态。
- `tools/OrderTools.java`：`@Tool` / `@ToolParam` 定义，仍返回自然语言文本。
- `service/OrderManageService.java`：模拟业务服务，返回 UUID，工具层按原文忽略该编号。
- `src/main/resources/prompts/refund-system.txt`：沿用文章的角色、确认步骤、退款范围及示例话术。
- `src/main/resources/static/index.html`：浏览器聊天页面。

Java 源文件均位于 `src/main/java/com/example/refund/` 下。

## 为了运行补齐的细节

- WebFlux 不使用 Servlet 的 `HttpServletResponse`；阻塞的初始化模型调用放到 boundedElastic 线程执行。
- 所选 Spring AI 版本通过 `MessageWindowChatMemory.maxMessages(100)` 配置窗口，不使用文章的请求级 `chat_memory_retrieve_size` 参数。
- 流式内容包装为 SSE：`message` 表示文本片段、`done` 表示正常结束、`failure` 表示请求失败。页面在结束或异常时关闭 EventSource，防止自动重连重复请求。
- 提供独立提示词文件、环境变量模板、聊天页面和自动化测试。

## 验证

```bash
./mvnw test
./mvnw package
```

`RefundFlowTest` 使用可控的 ChatModel 响应，不需要真实 API Key。覆盖初始化结构化输出、跨轮记忆/会话隔离、工具 JSON 参数到服务调用的映射、流式错误和 HTTP SSE。

这些测试验证接线，不证明真实模型会正确识别问题或完成流式工具调用。真实 DashScope 联调按上面的体验步骤执行。

## 本版范围

这是原文思路的基线版本，尚未应用讨论过的两处优化：初始化仍让模型输出后端已有字段，退款工具仍返回文本结果。对话记忆重启后丢失；不接数据库，不实现真实支付、鉴权、退款状态机或幂等控制。提示词中的到账时间是原文演示话术，没有实际资金到账含义。

原文没有给出完整工程，此项目依据文档代码片段补齐，并非作者源码副本。模型供应商与框架流式兼容问题需要结合当前依赖和实际请求定位。

## 参考

- [实战：仿 PDD 自动帮买家申请退款](https://thoughts.aliyun.com/workspaces/6963289eb0fc2e001bb052eb/docs/698829b8c71a890001c3d8e3)
- [Spring AI Alibaba 1.0.0.2 组件与依赖配置](https://www.java2ai.com/en/docs/1.0.0.2/tutorials/starters-and-quick-guide/)

GitHub 提交前先交互确认本次 commit message；当前实现仅供本地检查，未经用户确认不提交或推送。
