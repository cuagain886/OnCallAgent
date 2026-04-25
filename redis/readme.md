# Redis Docker 配置

本目录包含 SuperBizAgent 项目的 Redis Docker 配置文件。

## 服务说明

### 1. Redis 服务
- **容器名称**: `superbiz-redis`
- **镜像**: `redis:7.2-alpine`
- **端口**: `6379:6379`
- **数据持久化**: 启用 AOF 持久化
- **内存限制**: 256MB
- **淘汰策略**: allkeys-lru（最近最少使用）

### 2. Redis Commander（可选）
- **容器名称**: `superbiz-redis-commander`
- **镜像**: `rediscommander/redis-commander:latest`
- **端口**: `8081:8081`
- **访问地址**: http://localhost:8081
- **用途**: Redis Web 管理界面，方便查看和管理 Redis 数据

## 配置参数说明

### Redis 配置
```yaml
--appendonly yes                    # 启用 AOF 持久化
--appendfsync everysec             # 每秒同步一次
--save 900 1                      # 900秒内至少有1个key变化则保存
--save 300 10                     # 300秒内至少有10个key变化则保存
--save 60 10000                   # 60秒内至少有10000个key变化则保存
--maxmemory 256mb                  # 最大内存限制
--maxmemory-policy allkeys-lru     # 内存淘汰策略
--timeout 3000                    # 客户端空闲超时时间
--tcp-keepalive 60                # TCP 保活时间
```

### 应用配置对应
在 `application.yml` 中的配置：
```yaml
spring:
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
```

## 使用方法

### 1. 启动 Redis 服务
```bash
# 在项目根目录执行
cd manifest/docker/redis

# 启动服务
docker-compose up -d

# 查看日志
docker-compose logs -f redis
```

### 2. 停止 Redis 服务
```bash
# 停止服务
docker-compose down

# 停止并删除数据卷（谨慎使用）
docker-compose down -v
```

### 3. 重启 Redis 服务
```bash
docker-compose restart redis
```

### 4. 查看 Redis 状态
```bash
# 查看容器状态
docker-compose ps

# 查看 Redis 信息
docker exec -it superbiz-redis redis-cli info

# 查看所有 key
docker exec -it superbiz-redis redis-cli keys "*"

# 查看特定 key
docker exec -it superbiz-redis redis-cli get "session:xxx"
```

### 5. 访问 Redis Commander
启动服务后，可以通过浏览器访问：
```
http://localhost:8081
```

## 数据持久化

Redis 数据存储在 Docker 卷中：
```
manifest/docker/volumes/redis/
```

包含：
- `appendonly.aof` - AOF 持久化文件
- `dump.rdb` - RDB 快照文件

## 健康检查

Redis 服务配置了健康检查：
- **检查命令**: `redis-cli ping`
- **间隔**: 10秒
- **超时**: 3秒
- **重试次数**: 5次

## 网络配置

Redis 服务使用独立的 Docker 网络：
- **网络名称**: `superbiz`
- **驱动**: `bridge`

这样可以与其他服务（如 Milvus）隔离，也可以根据需要连接到同一个网络。

## 性能优化建议

### 1. 内存调整
根据实际使用情况调整 `maxmemory`：
```yaml
--maxmemory 512mb  # 增加到 512MB
```

### 2. 持久化策略
生产环境建议：
```yaml
--appendonly yes
--appendfsync everysec  # 平衡性能和数据安全
```

开发环境可以禁用持久化以提高性能：
```yaml
--appendonly no
```

### 3. 连接池配置
在 `application.yml` 中调整连接池参数：
```yaml
lettuce:
  pool:
    max-active: 16      # 增加最大连接数
    max-idle: 8        # 最大空闲连接数
    min-idle: 2        # 最小空闲连接数
    max-wait: 3000ms   # 最大等待时间
```

## 故障排查

### 1. Redis 无法启动
```bash
# 查看日志
docker-compose logs redis

# 检查端口占用
netstat -ano | findstr :6379  # Windows
lsof -i :6379                   # Linux/Mac
```

### 2. 连接超时
- 检查 `application.yml` 中的 `timeout` 配置
- 确认 Redis 服务已启动：`docker-compose ps`
- 检查防火墙设置

### 3. 数据丢失
- 检查数据卷权限
- 确认持久化配置正确
- 查看 AOF 文件：`docker exec -it superbiz-redis ls -la /data/`

## 监控

### 使用 Redis Commander
访问 http://localhost:8081 可以：
- 查看所有 key
- 查看 key 的值
- 执行 Redis 命令
- 监控 Redis 性能

### 使用命令行
```bash
# 查看内存使用
docker exec -it superbiz-redis redis-cli info memory

# 查看连接数
docker exec -it superbiz-redis redis-cli info clients

# 查看统计信息
docker exec -it superbiz-redis redis-cli info stats
```

## 安全建议

### 1. 设置密码
修改 `docker-compose.yml`：
```yaml
command: >
  redis-server
  --requirepass your_strong_password
  # ... 其他配置
```

同时更新 `application.yml`：
```yaml
spring:
  data:
    redis:
      password: your_strong_password
```

### 2. 限制访问
- 不要将 Redis 端口暴露到公网
- 使用防火墙限制访问
- 在生产环境中使用 VPN 或内网访问

### 3. 禁用危险命令
```yaml
command: >
  redis-server
  --rename-command FLUSHDB ""
  --rename-command FLUSHALL ""
  # ... 其他配置
```

## 备份与恢复

### 备份
```bash
# 备份 RDB 文件
docker cp superbiz-redis:/data/dump.rdb ./backup/dump_$(date +%Y%m%d).rdb

# 备份 AOF 文件
docker cp superbiz-redis:/data/appendonly.aof ./backup/aof_$(date +%Y%m%d).aof
```

### 恢复
```bash
# 停止 Redis
docker-compose stop redis

# 恢复文件
docker cp ./backup/dump_20240101.rdb superbiz-redis:/data/dump.rdb

# 启动 Redis
docker-compose start redis
```

## 与项目集成

Redis 主要用于：
1. **短期记忆持久化**：存储会话历史
2. **缓存**：提高访问速度
3. **分布式锁**：防止并发冲突

相关代码：
- `org.example.memory.ShortTermMemoryManager` - 短期记忆管理器
- `org.example.Hooks.MemoryPersistHook` - 记忆持久化 Hook

## 参考资料
- [Redis 官方文档](https://redis.io/documentation)
- [Redis Commander](https://github.com/joeferner/redis-commander)
- [Spring Data Redis](https://spring.io/projects/spring-data-redis)