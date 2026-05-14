# SuperBizAgent

> 基于 Spring Boot + AI Agent 的智能问答与运维系统

## 项目简介

企业级智能业务代理系统，包含三大核心模块：

### 1. RAG 智能问答
集成 Milvus 向量数据库和 DeepSeek 大模型，提供基于检索增强生成的智能问答能力，支持多轮对话和流式输出。

<img width="2382" height="1485" alt="image" src="https://github.com/user-attachments/assets/d3ad2b83-03e0-467f-bef0-26d28402d3f8" />


### 2. AIOps 智能运维
基于 AI Agent 的自动化运维系统，采用 Planner-Executor-Replanner 架构，实现告警分析、日志查询、智能诊断和报告生成。

<img width="2382" height="1485" alt="image" src="https://github.com/user-attachments/assets/21ad3947-520e-4550-a02c-4d63e5468505" />


### 3. 知识库自维护 Agent
基于四阶段 Multi-Agent 流水线（Extractor → Classifier → Generator → Indexer），实现 AIOps 故障报告自动沉淀为知识库文档，支持 CREATE/UPDATE 智能分类和 Human-in-the-Loop 审核。
<img width="2382" height="1485" alt="image" src="https://github.com/user-attachments/assets/2fd32518-6cb2-4ffe-903a-cf56bdc534fc" />


## 核心特性

- **RAG 问答**: 向量检索 + 多轮对话 + 流式输出 + Prompt 工程优化
- **AIOps 运维**: 智能诊断 + 多 Agent 协作 + 自动报告
- **三层记忆架构**: 短期记忆（ConcurrentHashMap + Redis）+ 长期记忆（Milvus 向量语义召回）+ LLM 摘要压缩
- **知识库自维护**: 四阶段 Agent 流水线 + CREATE/UPDATE 分类 + 人工审核 + WebSocket 实时推送
- **工具集成**: 文档检索、告警查询、日志分析、时间工具、知识库维护工具集
- **RAG 评估**: 基于 Ragas 框架的自动化评估流水线，覆盖 5 项核心指标
- **Web 界面**: 对话界面 + 知识库管理页面

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 3.2.0 | 应用框架 |
| Spring AI | 1.1.1 | AI Agent 框架（OpenAI 兼容模式） |
| Spring AI Alibaba | 1.1.0.0-RC2 | Agent 编排框架（ReactAgent、SupervisorAgent、Hooks） |
| DeepSeek API | - | 大语言模型（chat/reasoning） |
| DashScope | 2.17.0 | 阿里云 Embedding 服务（text-embedding-v4） |
| Milvus | 2.6.10 | 向量数据库 |
| Redis | 7.2 | 短期记忆持久化 |
| Ragas | 0.2.x | RAG 评估框架 |
| JUnit 5 + Mockito | 5.10.x / 5.7.x | 单元测试 |

## 核心模块

```
SuperBizAgent/
├── src/main/java/org/example/
│   ├── controller/
│   │   ├── ChatController.java                # 对话接口控制器
│   │   └── KnowledgeMaintenanceController.java # 知识库维护 API
│   ├── service/
│   │   ├── ChatService.java                   # 对话服务（含 Prompt 工程）
│   │   ├── AiOpsService.java                  # AIOps 多 Agent 服务
│   │   ├── KnowledgeMaintenanceService.java   # 知识库自维护服务
│   │   ├── KnowledgeWebSocketService.java     # WebSocket 推送服务
│   │   ├── RagService.java                    # RAG 流式服务
│   │   ├── VectorSearchService.java           # 向量检索服务
│   │   ├── VectorIndexService.java            # 向量索引服务
│   │   └── DocumentChunkService.java          # 文档分片服务
│   ├── agent/tool/
│   │   ├── DateTimeTools.java                 # 时间工具
│   │   ├── InternalDocsTools.java             # 文档检索（RAG）
│   │   ├── QueryMetricsTools.java             # 告警查询
│   │   ├── QueryLogsTools.java                # 日志查询
│   │   └── knowledge/                         # 知识库维护工具集
│   │       ├── DocSearchTool.java             # 语义搜索
│   │       ├── DocListTool.java               # 文档列表
│   │       ├── DocWriteTool.java              # 文档写入/更新
│   │       ├── DocIndexTool.java              # 向量化入库
│   │       ├── QualityTool.java               # 质量评估
│   │       └── TemplateTool.java              # 模板管理
│   ├── memory/                                # 三层记忆系统
│   │   ├── ShortTermMemoryManager.java        # 短期记忆（ConcurrentHashMap + Redis）
│   │   ├── LongTermMemoryManager.java         # 长期记忆（Milvus 向量）
│   │   ├── MemoryCompressor.java              # LLM 对话压缩
│   │   └── MemoryTransformer.java             # 短期→长期迁移
│   ├── Hooks/
│   │   ├── MemoryRecallHook.java              # BEFORE_MODEL 记忆召回
│   │   └── MemoryPersistHook.java             # AFTER_MODEL 记忆持久化
│   ├── model/knowledge/                       # 知识库维护数据模型
│   │   ├── TaskStatus.java                    # 任务状态枚举
│   │   ├── MaintenanceTask.java               # 维护任务
│   │   ├── ExtractorResult.java               # 提取结果
│   │   ├── ClassifierResult.java              # 分类结果
│   │   ├── GeneratorResult.java               # 生成结果
│   │   └── IndexerResult.java                 # 入库结果
│   └── config/                                # 配置类
├── src/main/resources/
│   ├── static/
│   │   ├── index.html                         # 对话界面
│   │   └── knowledge.html                     # 知识库管理页面
│   └── application.yml
├── src/test/java/org/example/
│   ├── memory/                                # 记忆模块测试
│   ├── Hooks/                                 # Hook 测试
│   ├── service/                               # 服务测试
│   └── agent/tool/knowledge/                  # 知识库工具测试
├── aiops-docs/                                # 运维文档库（9 个场景）
├── rag_eval/                                  # RAG 评估流水线
└── docs/                                      # 项目文档
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

### 2. AIOps 智能运维接口

```bash
POST /api/ai_ops
```
自动执行告警分析流程，生成运维报告（SSE 流式输出）。

### 3. 知识库维护接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge/maintain` | 触发维护任务 |
| GET | `/api/knowledge/task/{taskId}` | 查询任务详情 |
| GET | `/api/knowledge/tasks?status=` | 列出任务（可按状态过滤） |
| POST | `/api/knowledge/review/{taskId}` | 审核任务（approve/reject） |
| GET | `/api/knowledge/pending-review` | 待审核列表 |
| GET | `/api/knowledge/stats` | 统计信息 |
| GET | `/api/knowledge/documents` | 文档列表 |

### 4. 会话管理

- `POST /api/chat/clear` - 清空会话历史
- `GET /api/chat/session/{sessionId}` - 获取会话信息

### 5. 文件管理

- `POST /api/upload` - 上传文件并自动向量化
- `GET /milvus/health` - Milvus 健康检查

## 记忆管理

三层记忆架构，支持跨会话的上下文持久化与语义检索：

```
┌─────────────────────────────────────────────────┐
│  Layer 1: 短期记忆                                │
│  ConcurrentHashMap（内存）+ Redis（持久化）        │
│  滑动窗口：最近 6 轮对话（12 条消息）              │
├─────────────────────────────────────────────────┤
│  Layer 2: 长期记忆                                │
│  Milvus 向量数据库，独立 "memory" 集合             │
│  语义召回 + Session 级别隔离                       │
├─────────────────────────────────────────────────┤
│  Layer 3: 记忆压缩                                │
│  LLM 摘要沉淀，重要性评估（多语言关键词）           │
└─────────────────────────────────────────────────┘
```

**Hook 机制**：
- `MemoryRecallHook`（BEFORE_MODEL）：从长期记忆召回相关上下文注入系统消息
- `MemoryPersistHook`（AFTER_MODEL）：异步持久化对话，Session 级串行化队列保证顺序一致性

## 知识库自维护

四阶段 Agent 流水线，实现 AIOps 报告自动沉淀为知识库文档：

```
AIOps 报告 → Extractor（信息提取）→ Classifier（场景分类）→ Generator（文档生成）→ Indexer（质量入库）
                                      │
                                      ├── CREATE：新建文档
                                      ├── UPDATE：合并更新已有文档
                                      └── SKIP：已有文档覆盖
```

**审核机制**：支持 `autoApprove` 模式切换：
- `autoApprove=true`：自动通过，直接入库
- `autoApprove=false`：进入 PENDING_REVIEW，等待人工审核

**前端管理**：`/knowledge.html` 提供统计面板、任务列表、审核界面、文档浏览。

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

# DashScope Embedding
dashscope:
  api-key: "${DASHSCOPE_API_KEY}"
  embedding:
    model: "text-embedding-v4"

# 记忆系统
memory:
  hooks:
    enabled: true
    recall:
      top-k: 3
      max-context-length: 1500
    persist:
      async: true
  short-term:
    max-window-size: 6
    session-ttl: 3600
    enable-redis: true
  long-term:
    enable-auto-save: true
    save-threshold: 3
    importance-threshold: 0.7
  compression:
    threshold: 10

# 知识库维护
knowledge-maintenance:
  enabled: true
  auto-trigger: true
  max-retry: 1
  quality-threshold: 0.7
  confidence-threshold: 0.5
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
# 1. 启动向量数据库 + Redis
docker compose up -d

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
http://localhost:9900           # 对话界面
http://localhost:9900/knowledge.html  # 知识库管理
```

**命令行**
```bash
# 智能问答
curl -X POST http://localhost:9900/api/chat \
  -H "Content-Type: application/json" \
  -d '{"Id":"test","Question":"CPU使用率过高怎么办？"}'

# 触发知识库维护
curl -X POST http://localhost:9900/api/knowledge/maintain \
  -H "Content-Type: application/json" \
  -d '{"reportContent":"# CPU 高负载告警\n\n症状：CPU 使用率 95%\n根因：死循环"}'

# 上传文档
curl -X POST http://localhost:9900/api/upload -F "file=@document.txt"

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
pip install -r requirements-ragas.txt
python run_evaluation.py
```

### 评估优化历程

1. **Prompt 工程优化**: 去除注入 LLM 的 JSON 元数据噪声，设计结构化 prompt 模板
2. **评估数据质量提升**: 重写 ground_truth 从知识库原文提取关键句，解决粒度不匹配问题
3. **文档分片优化**: 在切片内容前注入章节标题，增强向量语义匹配
4. **评估模型升级**: Judge 模型切换为 deepseek-v4-pro，提升事实判断精度

**版本**: v3.0.0
**许可证**: MIT
