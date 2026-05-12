package org.example.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务：用户管理、登录验证
 * 用户数据存储在 Redis 中，key 格式 "auth:user:{username}"
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final String USER_KEY_PREFIX = "auth:user:";
    private static final long USER_TTL_DAYS = 365;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 初始化默认管理员（首次启动时，Redis 可用才执行）
     */
    public void initDefaultAdmin(String username, String password) {
        if (redisTemplate == null) {
            logger.warn("RedisTemplate 未注入，跳过管理员初始化");
            return;
        }
        try {
            // 测试 Redis 连接
            redisTemplate.hasKey("test:connection");
        } catch (Exception e) {
            logger.warn("Redis 连接失败: {}", e.getMessage());
            return;
        }
        if (getUser(username) == null) {
            createUser(username, password, "admin", "管理员");
            logger.info("已创建默认管理员: {}", username);
        }
    }

    /**
     * 用户登录
     */
    public LoginResult login(String username, String password) {
        UserAccount user = getUser(username);
        if (user == null) {
            return LoginResult.failure("用户不存在");
        }
        if (!user.isEnabled()) {
            return LoginResult.failure("账户已被禁用");
        }
        if (!user.getPassword().equals(password)) {
            return LoginResult.failure("密码错误");
        }

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        saveUser(user);

        // 生成 Token
        String token = jwtUtil.generateToken(username, user.getRole());
        logger.info("用户登录成功: {}", username);

        return LoginResult.success(token, username, user.getRole(), user.getDisplayName());
    }

    /**
     * 用户注册
     */
    public boolean register(String username, String password, String displayName) {
        if (getUser(username) != null) {
            return false;  // 用户已存在
        }
        createUser(username, password, "user", displayName);
        logger.info("新用户注册: {}", username);
        return true;
    }

    /**
     * 创建用户
     */
    public void createUser(String username, String password, String role, String displayName) {
        UserAccount user = UserAccount.builder()
                .username(username)
                .password(password)
                .role(role)
                .displayName(displayName != null ? displayName : username)
                .createdAt(LocalDateTime.now())
                .enabled(true)
                .build();
        saveUser(user);
    }

    /**
     * 获取用户
     */
    public UserAccount getUser(String username) {
        if (redisTemplate == null) {
            logger.warn("Redis 未连接，无法获取用户: {}", username);
            return null;
        }
        try {
            return (UserAccount) redisTemplate.opsForValue().get(USER_KEY_PREFIX + username);
        } catch (Exception e) {
            logger.error("获取用户失败: {}", username, e);
            return null;
        }
    }

    /**
     * 保存用户
     */
    private void saveUser(UserAccount user) {
        if (redisTemplate == null) {
            logger.warn("Redis 未连接，无法保存用户: {}", user.getUsername());
            return;
        }
        redisTemplate.opsForValue().set(
                USER_KEY_PREFIX + user.getUsername(),
                user,
                USER_TTL_DAYS,
                TimeUnit.DAYS
        );
    }

    /**
     * 列出所有用户（不含密码）
     */
    public List<Map<String, Object>> listUsers() {
        if (redisTemplate == null) {
            logger.warn("Redis 未连接，无法列出用户");
            return Collections.emptyList();
        }
        Set<String> keys = redisTemplate.keys(USER_KEY_PREFIX + "*");
        List<Map<String, Object>> users = new ArrayList<>();
        if (keys != null) {
            for (String key : keys) {
                try {
                    UserAccount user = (UserAccount) redisTemplate.opsForValue().get(key);
                    if (user != null) {
                        Map<String, Object> info = new HashMap<>();
                        info.put("username", user.getUsername());
                        info.put("role", user.getRole());
                        info.put("displayName", user.getDisplayName());
                        info.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : "");
                        info.put("lastLoginAt", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : "");
                        info.put("enabled", user.isEnabled());
                        users.add(info);
                    }
                } catch (Exception ignored) {}
            }
        }
        return users;
    }

    /**
     * 禁用/启用用户
     */
    public boolean setUserEnabled(String username, boolean enabled) {
        UserAccount user = getUser(username);
        if (user == null) return false;
        user.setEnabled(enabled);
        saveUser(user);
        return true;
    }

    /**
     * 修改密码
     */
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        UserAccount user = getUser(username);
        if (user == null) return false;
        if (!user.getPassword().equals(oldPassword)) return false;
        user.setPassword(newPassword);
        saveUser(user);
        return true;
    }

    /**
     * 登录结果
     */
    public record LoginResult(boolean success, String token, String username,
                              String role, String displayName, String errorMessage) {
        public static LoginResult success(String token, String username, String role, String displayName) {
            return new LoginResult(true, token, username, role, displayName, null);
        }
        public static LoginResult failure(String errorMessage) {
            return new LoginResult(false, null, null, null, null, errorMessage);
        }
    }
}
