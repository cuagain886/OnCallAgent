# MiniMax LLM 提供商迁移指南

本文档指导如何将 Java SuperBizAgent 从 DashScope（阿里云）迁移到 MiniMax（通过 NVIDIA Catalog）。

## 概述

- **当前提供商**: DashScope（Alibaba）- 模型: qwen-plus, qvq-max
- **目标提供商**: MiniMax（通过 NVIDIA API Catalog）- 模型: minimaxai/minimax-m2.7
- **API 方式**: OpenAI 兼容接口（简化集成）
- **Base URL**: https://integrate.api.nvidia.com/v1
- **认证方式**: NVIDIA API Key

## 修改步骤

### 第一步：获取 NVIDIA API Key

1. 访问 [NVIDIA API Catalog](https://build.nvidia.com/multimodal/llama-vision)
2. 注册或登录账户
3. 创建新的 API Key
4. 设置环境变量：

```bash
# Windows (PowerShell)
$env:NVIDIA_API_KEY = "your-nvidia-api-key-here"

# Linux/Mac
export NVIDIA_API_KEY="your-nvidia-api-key-here"
```

### 第二步：修改 pom.xml

**删除以下 DashScope 相关依赖**:

```xml
<!-- 删除这些依赖 -->
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
</dependency>

<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-agent-framework</artifactId>
</dependency>

<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>dashscope-sdk-java</artifactId>
    <version>2.17.0</version>
</dependency>
```

**添加以下 OpenAI 依赖**:

```xml
<!-- Spring AI OpenAI (用于 MiniMax 和其他 OpenAI 兼容 API) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>

<!-- OkHttp for HTTP requests -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.11.0</version>
</dependency>
```

### 第三步：修改 application.yml

**完整的 application.yml 配置**:

```yaml
server:
  port: 9900
  servlet:
    encoding:
      charset: UTF-8
      enabled: true
      force: true

file:
  upload:
    path: ./uploads
    allowed-extensions: txt,md

milvus:
  host: localhost
  port: 19530
  username: ""
  password: ""
  database: default
  timeout: 10000

# Spring AI OpenAI 配置（用于调用 MiniMax）
spring:
  ai:
    openai:
      api-key: ${NVIDIA_API_KEY}  # 从环境变量读取
      base-url: https://integrate.api.nvidia.com/v1
      chat:
        options:
          model: minimaxai/minimax-m2.7
          temperature: 1
          top-p: 0.95
          max-tokens: 8192
      retry:
        max-attempts: 3
        backoff:
          initial-interval: 2000
          multiplier: 2
          max-interval: 10000
    
    # Spring AI MCP 客户端配置（如果使用）
    mcp:
      client:
        enabled: true
        name: tencent-mcp-server
        version: 1.0.0
        request-timeout: 60s
        type: ASYNC
        sse:
          connections:
            tencent-cls:
              url: https://mcp-api.tencent-cloud.com
              sse-endpoint: ${TENCENT_MCP_SSE_ENDPOINT}
    
    data:
      redis:
        host: localhost
        port: 6379
        password:
        database: 0
        timeout: 3000
        lettuce:
          pool:
            max-active: 8
            max-idle: 8
            min-idle: 0
            max-wait: -1ms

# 文档分片配置
document:
  chunk:
    max-size: 800
    overlap: 100

# 模型配置 - MiniMax 模型名
model:
  chat-model: "minimaxai/minimax-m2.7"
  rag-model: "minimaxai/minimax-m2.7"
  aiops-model: "minimaxai/minimax-m2.7"
  aiops-supervisor-model: "minimaxai/minimax-lite"
  embedding-model: "text-embedding-v4"  # 可改为 MiniMax 嵌入模型

# RAG 配置
rag:
  top-k: 3

# Prometheus 配置
prometheus:
  base-url: http://localhost:9090
  timeout: 10
  mock-enabled: true

# CLS 日志配置
cls:
  mock-enabled: true

memory:
  hooks:
    enabled: true
```

### 第四步：已修改的 Java 文件

#### RagService.java ✅ 已修改

已从 DashScope SDK 迁移到 Spring AI OpenAI ChatModel:

**主要改动**:
- ✅ 移除 `dashscope-sdk-java` 导入
- ✅ 添加 `spring-ai-openai` 导入
- ✅ 注入 `org.springframework.ai.chat.ChatModel`
- ✅ 替换 `generateAnswerStream()` 使用 OpenAI API

**验证方式**:
```java
// 现在使用 Spring AI ChatModel
@Autowired
private ChatModel chatModel;

// 调用 MiniMax
ChatResponse response = chatModel.call(
    new Prompt(messages, OpenAiChatOptions.builder()
        .model("minimaxai/minimax-m2.7")
        .build())
);
```

#### ChatService.java ⚠️ 部分修改

**已修改**:
- ✅ 导入语句更新，移除 DashScope 相关导入
- ✅ 添加 `ChatModel` 注入
- ✅ 模型名称配置更新为 MiniMax 模型

**需要后续完善**:
- ReactAgent 和 graph 工具需要进一步迁移
- 建议在后续版本中完全替换

### 第五步：更新 Controller 代码

#### ChatController.java 需要更新

搜索并替换所有 `createDashScopeApi()` 调用：

```bash
# 搜索：
grep -r "createDashScopeApi\|DashScopeApi\|DashScopeChatModel" src/main/java/

# 需要手动替换这些调用为 Spring AI 的 ChatModel
```

**示例修改**:

```java
// 旧代码
DashScopeApi dashScopeApi = chatService.createDashScopeApi();
DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

// 新代码
ChatModel chatModel = chatService.getChatModel();
```

### 第六步：编译和测试

```bash
# 1. 清理并重新构建
mvn clean install

# 2. 启动应用
java -jar target/super-biz-agent-1.0-SNAPSHOT.jar

# 3. 测试 RAG API
curl -X POST http://localhost:9900/api/chat \
  -H "Content-Type: application/json" \
  -d '{"id": "test-session", "question": "CPU使用率过高怎么办?"}'
```

### 第七步：验证功能

1. **RAG 功能测试**:
   ```bash
   python rag_eval/evals.py
   ```

2. **API 端点测试**:
   - `/api/chat` - 基础对话
   - `/api/stream-chat` - 流式对话（如果支持）

3. **日志检查**:
   ```bash
   # 检查 MiniMax API 调用日志
   tail -f logs/application.log | grep -i "minimax\|openai"
   ```

## 模型对应关系

| 用途 | 旧模型（DashScope） | 新模型（MiniMax） |
|------|-------------------|------------------|
| 对话 | qwen-plus-2025-12-01 | minimaxai/minimax-m2.7 |
| RAG | qvq-max-2025-03-25 | minimaxai/minimax-m2.7 |
| AIOps | qwen-plus-2025-12-01 | minimaxai/minimax-m2.7 |
| 路由 | qwen-turbo | minimaxai/minimax-lite |
| 嵌入 | text-embedding-v4 | 待选择 |

## 故障排除

### 1. API Key 无效错误

```
Error: 401 Unauthorized - Invalid API Key
```

**解决方案**:
```bash
# 验证环境变量
echo $NVIDIA_API_KEY

# 重新设置 API Key
export NVIDIA_API_KEY="your-correct-key"

# 重启应用
```

### 2. 超时错误

```
Error: Request timeout
```

**解决方案**:
- 检查网络连接
- 增加超时时间（application.yml 中的 timeout 字段）
- 检查 NVIDIA API 服务状态

### 3. 模型不可用

```
Error: Model not found - minimaxai/minimax-m2.7
```

**解决方案**:
- 确保 NVIDIA Catalog 中有该模型
- 检查模型名称是否正确
- 访问 https://build.nvidia.com 验证可用模型

## 性能对比

| 指标 | DashScope | MiniMax | 备注 |
|-----|----------|--------|------|
| 响应延迟 | ~1-2s | ~1-2s | 类似 |
| 成本 | 按调用次数 | NVIDIA 定价 | 需比对 |
| 模型能力 | 强 | 强 | 都支持多模态 |

## 回滚方案

如需回滚到 DashScope:

1. 恢复 pom.xml 的 DashScope 依赖
2. 恢复 application.yml 的 DashScope 配置
3. 恢复 RagService.java 和 ChatService.java 的原始版本
4. 重新编译运行

## 相关文档

- [Spring AI OpenAI](https://spring.io/projects/spring-ai)
- [NVIDIA API Catalog](https://build.nvidia.com/)
- [MiniMax API 文档](https://www.minimaxi.com/document/guides/overview)

## 下一步计划

- [ ] 完整迁移 ChatService 和 ReactAgent
- [ ] 支持 MiniMax 原生嵌入模型
- [ ] 性能基准测试
- [ ] 成本分析和优化
- [ ] 多提供商支持（抽象 LLM 接口层）

---

**更新时间**: 2026-04-30  
**维护者**: AI Team
