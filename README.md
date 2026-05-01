# SuperBizAgent

> 基于 Spring Boot + AI Agent 的智能问答与运维系统

## 项目简介

企业级智能业务代理系统，包含两大核心模块：

### 1. RAG 智能问答
集成 Milvus 向量数据库和 DeepSeek 大模型，提供基于检索增强生成的智能问答能力，支持多轮对话和流式输出。

<img width="2382" height="1485" alt="0fd182ebe96dfb3c179bc7bc5a1f5705" src="https://github.com/user-attachments/assets/b0a2dbb5-f8f9-4439-839b-ea2bde1ad31c" />


### 2. AIOps 智能运维
基于 AI Agent 的自动化运维系统，采用 Planner-Executor-Replanner 架构，实现告警分析、日志查询、智能诊断和报告生成。

<img width="2382" height="1485" alt="7c3b37c5681bde19d2f232ce31236f5d" src="https://github.com/user-attachments/assets/9c198a54-d3a0-4a43-8b7b-b2c9fe99314f" />


## 核心特性

- **RAG 问答**: 向量检索 + 多轮对话 + 流式输出 + Prompt 工程优化
- **AIOps 运维**: 智能诊断 + 多 Agent 协作 + 自动报告
- **工具集成**: 文档检索、告警查询、日志分析、时间工具
- **会话管理**: 上下文维护、历史管理、自动清理
- **RAG 评估**: 基于 Ragas 框架的自动化评估流水线，覆盖 5 项核心指标
- **Web 界面**: 提供测试界面和 RESTful API

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 3.2.0 | 应用框架 |
| Spring AI | 1.1.1 | AI Agent 框架（OpenAI 兼容模式） |
| DeepSeek API | - | 大语言模型（chat/reasoning） |
| DashScope | 2.17.0 | 阿里云 Embedding 服务（text-embedding-v4） |
| Milvus | 2.6.10 | 向量数据库 |
| Ragas | 0.2.x | RAG 评估框架 |

## 核心模块

```
SuperBizAgent/
├── src/main/java/org/example/
│   ├── controller/
│   │   └── ChatController.java        # 统一接口控制器
│   ├── service/
│   │   ├── ChatService.java           # 对话服务（含 Prompt 工程）
│   │   ├── AiOpsService.java          # AIOps 服务
│   │   ├── RagService.java            # RAG 流式服务
│   │   ├── VectorSearchService.java   # 向量检索服务
│   │   ├── VectorIndexService.java    # 向量索引服务
│   │   └── DocumentChunkService.java  # 文档分片服务
│   ├── agent/tool/                    # Agent 工具集
│   │   ├── DateTimeTools.java         # 时间工具
│   │   ├── InternalDocsTools.java     # 文档检索（RAG）
│   │   ├── QueryMetricsTools.java     # 告警查询
│   │   └── QueryLogsTools.java        # 日志查询
│   ├── agent/supervisor/              # AIOps Supervisor Agent
│   ├── agent/planner/                 # AIOps Planner Agent
│   ├── agent/executor/                # AIOps Executor Agent
│   ├── Hooks/                         # 记忆 Hooks
│   └── config/                        # 配置类
├── src/main/resources/
│   ├── static/                        # Web 界面
│   └── application.yml                # 应用配置
├── aiops-docs/                        # 运维文档库（9 个场景）
├── rag_eval/                          # RAG 评估流水线
│   ├── evals.py                       # 测试数据集生成（调用 Java API）
│   ├── ragas_eval.py                  # Ragas 指标评估
│   ├── run_evaluation.py              # 评估编排脚本
│   └── evals/
│       ├── datasets/                  # 评估数据集（CSV）
│       └── result/                    # 评估结果（JSON + Markdown）
└── docs/                              # 项目文档
```

## 核心接口

### 1. 智能问答接口

**流式对话（推荐）**
```bash
POST /api/chat_stream
Content-Type: application/json

{
  "Id": "session-123",
  "Question": "什么是向量数据库？"
}
```
支持 SSE 流式输出、自动工具调用、多轮对话。

**普通对话**
```bash
POST /api/chat
Content-Type: application/json

{
  "Id": "session-123",
  "Question": "什么是向量数据库？"
}
```
一次性返回完整结果，支持工具调用和多轮对话。响应包含 `retrievedContexts` 字段，用于 RAG 评估。

### 2. AIOps 智能运维接口

```bash
POST /api/ai_ops
```
自动执行告警分析流程，生成运维报告（SSE 流式输出）。

### 3. 会话管理

- `POST /api/chat/clear` - 清空会话历史
- `GET /api/chat/session/{sessionId}` - 获取会话信息

### 4. 文件管理

- `POST /api/upload` - 上传文件并自动向量化
- `GET /milvus/health` - Milvus 健康检查

## 核心配置

### application.yml

```yaml
server:
  port: 9900

# Milvus 向量数据库
milvus:
  host: localhost
  port: 19530

# DeepSeek API（OpenAI 兼容模式）
spring:
  ai:
    openai:
      api-key: "${DEEPSEEK_API_KEY}"
      base-url: https://api.deepseek.com

# 模型配置
model:
  chat-model: "deepseek-chat"
  rag-model: "deepseek-chat"
  aiops-model: "deepseek-chat"
  aiops-supervisor-model: "deepseek-chat"
  embedding-model: "text-embedding-v4"

# DashScope（仅用于 Embedding）
dashscope:
  api-key: "${DASHSCOPE_API_KEY}"
  embedding:
    model: "text-embedding-v4"

# RAG 配置
rag:
  top-k: 3

# 文档分片
document:
  chunk:
    max-size: 800
    overlap: 100
```

### 环境变量

```bash
export DEEPSEEK_API_KEY=your-deepseek-api-key
export DASHSCOPE_API_KEY=your-dashscope-api-key
```

## 快速开始

### 1. 环境准备

```bash
# 设置 API Key
export DEEPSEEK_API_KEY=your-deepseek-api-key
export DASHSCOPE_API_KEY=your-dashscope-api-key
```

### 2. 启动应用

方法一：手动启动
```bash
# 1. 启动向量数据库
docker compose up -d -f vector-database.yml

# 2. 启动服务
mvn clean install
mvn spring-boot:run
```

方法二：一键启动
```bash
make init  # 会自动启动向量数据库并上传运维文档到向量库
```

### 3. 使用示例

**Web 界面**
```
http://localhost:9900
```

**命令行**
```bash
# 上传文档
curl -X POST http://localhost:9900/api/upload \
  -F "file=@document.txt"

# 智能问答
curl -X POST http://localhost:9900/api/chat \
  -H "Content-Type: application/json" \
  -d '{"Id":"test","Question":"CPU使用率过高怎么办？"}'

# 健康检查
curl http://localhost:9900/milvus/health
```

## RAG 评估

项目内置基于 Ragas 框架的 RAG 自动化评估流水线，覆盖 5 项核心指标：

| 指标 | 说明 | 最终得分 |
|------|------|----------|
| Faithfulness | 答案对检索上下文的忠实度 | 0.96 |
| Context Recall | 检索对参考标准的覆盖度 | 0.91 |
| Context Precision | 检索结果的精准度 | 0.90 |
| Factual Correctness | 答案的事实正确性 | 0.74 |
| Answer Relevancy | 答案与问题的相关性 | 0.85 |
| **综合得分** | — | **0.87** |

### 运行评估

```bash
cd rag_eval

# 安装依赖
pip install -r requirements-ragas.txt

# 生成测试数据集（调用 Java API）
python evals.py

# 运行 Ragas 评估
python ragas_eval.py

# 一键运行（生成数据集 + 评估）
python run_evaluation.py
```

### 评估优化历程

1. **Prompt 工程优化**: 去除注入 LLM 的 JSON 元数据噪声，设计结构化 prompt 模板
2. **评估数据质量提升**: 重写 ground_truth 从知识库原文提取关键句，解决粒度不匹配问题
3. **文档分片优化**: 在切片内容前注入章节标题，增强向量语义匹配
4. **评估模型升级**: Judge 模型切换为 deepseek-v4-pro，提升事实判断精度

**版本**: v2.0.0
**许可证**: MIT
