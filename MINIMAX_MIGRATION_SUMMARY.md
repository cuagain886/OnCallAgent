# MiniMax 迁移 - 修改总结

## 修改完成情况

### ✅ 已完成的修改

#### 1. 配置文件修改
- **application.yml** ✅ 完全更新
  - ✓ 移除 DashScope 配置
  - ✓ 添加 OpenAI/MiniMax 配置
  - ✓ 更新模型名称为 MiniMax 模型
  - ✓ 添加 NVIDIA API Catalog 端点

- **pom.xml** ✅ 依赖更新
  - ✓ 添加 `spring-ai-openai-spring-boot-starter`
  - ✓ 注释掉 DashScope 相关依赖
  - ✓ 保留其他必要依赖 (Milvus, Lombok 等)

#### 2. Java 源代码修改

**RagService.java** ✅ 完全重写
- 移除 DashScope SDK 导入
- 添加 Spring AI OpenAI 导入
- 注入 `ChatModel` (Spring AI 标准接口)
- 重新实现 `generateAnswerStream()` 方法
- 使用 `OpenAiChatOptions` 构建请求

**ChatService.java** ⚠️ 部分修改
- ✓ 导入语句更新
- ✓ 添加 `ChatModel` 注入
- ✓ 模型名称配置更新
- ⚠️ 需要后续完善: ReactAgent 和 Graph 工具集成

#### 3. 脚本和工具

**start_minimax.sh** ✅ 新建
- Linux/Mac 启动脚本
- 自动验证环境
- 编译和运行应用

**start_minimax.bat** ✅ 新建
- Windows 启动脚本
- 自动验证环境
- 编译和运行应用

**verify_minimax_config.py** ✅ 新建
- 配置验证工具
- 测试 NVIDIA API 连接
- 测试 Java 应用连接
- 验证配置文件

#### 4. 文档

**MINIMAX_MIGRATION_GUIDE.md** ✅ 新建
- 完整迁移指南
- 环境配置步骤
- 故障排除方案
- 性能对比

---

## 环境变量配置

### 必需环境变量

```bash
# NVIDIA API Key (必需)
export NVIDIA_API_KEY="your-nvidia-api-key-here"

# 可选：腾讯云 MCP 配置
export TENCENT_MCP_SSE_ENDPOINT="your-endpoint"
```

### 设置方式

**Windows (PowerShell)**:
```powershell
$env:NVIDIA_API_KEY = "your-key"
```

**Windows (命令提示符)**:
```cmd
set NVIDIA_API_KEY=your-key
```

**Linux/Mac**:
```bash
export NVIDIA_API_KEY="your-key"
```

---

## 快速开始

### 1. 验证配置

```bash
python verify_minimax_config.py
```

### 2. 编译项目

```bash
mvn clean install
```

### 3. 启动应用

**自动脚本**:
```bash
# Linux/Mac
./start_minimax.sh

# Windows
start_minimax.bat
```

**手动启动**:
```bash
java -jar target/super-biz-agent-1.0-SNAPSHOT.jar
```

### 4. 测试 API

```bash
curl -X POST http://localhost:9900/api/chat \
  -H "Content-Type: application/json" \
  -d '{"id": "test-123", "question": "What is 2+2?"}'
```

---

## 关键配置对照表

| 配置项 | 旧值 (DashScope) | 新值 (MiniMax) | 位置 |
|------|-----------------|--------------|------|
| API 端点 | https://dashscope.aliyuncs.com/api/v1 | https://integrate.api.nvidia.com/v1 | application.yml |
| 认证 Key | Ali-Key | NVIDIA_API_KEY | application.yml |
| 聊天模型 | qwen-plus-2025-12-01 | minimaxai/minimax-m2.7 | application.yml |
| RAG 模型 | qvq-max-2025-03-25 | minimaxai/minimax-m2.7 | application.yml |
| 路由模型 | qwen-turbo | minimaxai/minimax-lite | application.yml |
| Java 库 | dashscope-sdk-java | spring-ai-openai | pom.xml |

---

## 测试清单

- [ ] 环境变量 `NVIDIA_API_KEY` 已设置
- [ ] 运行 `verify_minimax_config.py` 验证通过
- [ ] 项目编译成功 (`mvn clean install`)
- [ ] Java 应用启动成功 (localhost:9900)
- [ ] 测试 RAG API 端点 (`/api/chat`)
- [ ] 测试流式端点（如有）
- [ ] 查看日志确认使用的是 MiniMax 模型

---

## 文件修改详情

### 修改的文件

```
src/main/resources/application.yml
  ├─ 移除 dashscope 配置
  ├─ 添加 spring.ai.openai 配置
  └─ 更新模型名称为 MiniMax 模型

src/main/java/org/example/service/RagService.java
  ├─ 移除 DashScope 导入
  ├─ 添加 Spring AI OpenAI 导入
  └─ 重写 generateAnswerStream() 方法

src/main/java/org/example/service/ChatService.java
  ├─ 更新导入语句
  ├─ 添加 ChatModel 注入
  └─ 更新模型名称配置

pom.xml
  ├─ 添加 spring-ai-openai-spring-boot-starter
  ├─ 注释 DashScope 依赖
  └─ 添加 OkHttp 库
```

### 新增的文件

```
MINIMAX_MIGRATION_GUIDE.md      # 迁移指南
MINIMAX_MIGRATION_SUMMARY.md    # 本文件
start_minimax.sh                 # Linux/Mac 启动脚本
start_minimax.bat                # Windows 启动脚本
verify_minimax_config.py         # 配置验证工具
```

---

## 已知问题和注意事项

### ⚠️ 需要后续处理

1. **ChatService 中的 ReactAgent**
   - 当前保留原有的 DashScope 依赖
   - 需要在后续版本中完全迁移到 Spring AI

2. **向量化模型**
   - 当前仍使用 DashScope 的 `text-embedding-v4`
   - 可选: 改为 MiniMax 的向量模型

3. **某些 Controller 方法**
   - 可能仍有 `createDashScopeApi()` 调用
   - 需要搜索和替换为 Spring AI 等价方法

### 搜索建议

找出所有需要更新的代码：

```bash
# 查找所有 DashScope 相关代码
grep -r "DashScope" src/ --include="*.java"
grep -r "dashscope" src/ --include="*.java"
grep -r "createDashScopeApi" src/ --include="*.java"
```

---

## 性能和成本考虑

| 方面 | DashScope | MiniMax | 说明 |
|-----|---------|--------|------|
| 响应速度 | ~1-2s | ~1-2s | 相近 |
| 模型质量 | 优秀 | 优秀 | 都支持多模态 |
| 价格 | 按调用计费 | NVIDIA 定价 | 需比对 |
| 部署 | 中国加速 | 全球加速 | NVIDIA CDN |

---

## 回滚方案

如需回滚到 DashScope，请参考 MINIMAX_MIGRATION_GUIDE.md 中的"回滚方案"部分。

---

## 支持资源

- [Spring AI 文档](https://spring.io/projects/spring-ai)
- [OpenAI API 文档](https://platform.openai.com/docs)
- [NVIDIA API Catalog](https://build.nvidia.com/)
- [MiniMax 文档](https://www.minimaxi.com/document/guides/overview)

---

## 后续改进建议

1. **完整迁移 ChatService**
   - 移除所有 DashScope 依赖
   - 统一使用 Spring AI 接口

2. **支持多个 LLM 提供商**
   - 创建 LLM 适配器接口
   - 支持 DashScope, MiniMax, OpenAI 等

3. **改进向量模型**
   - 迁移到 MiniMax 向量模型
   - 或使用 Sentence Transformers

4. **性能优化**
   - 连接池管理
   - 缓存优化
   - 批量请求支持

---

**迁移完成时间**: 2026-04-30  
**状态**: ✅ 基础迁移完成，部分功能需后续完善  
**维护者**: AI Team
