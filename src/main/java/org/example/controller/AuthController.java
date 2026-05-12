package org.example.controller;

import org.example.auth.AuthService;
import org.example.auth.AuthService.LoginResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 认证 API：登录、注册、用户管理
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthService authService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }

        LoginResult result = authService.login(username, password);
        if (result.success()) {
            return ResponseEntity.ok(Map.of(
                    "token", result.token(),
                    "username", result.username(),
                    "role", result.role(),
                    "displayName", result.displayName() != null ? result.displayName() : username
            ));
        } else {
            return ResponseEntity.status(401).body(Map.of("error", result.errorMessage()));
        }
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String displayName = request.getOrDefault("displayName", username);

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }
        if (username.length() < 3 || username.length() > 32) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名长度 3~32 字符"));
        }
        if (password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码长度至少 6 字符"));
        }

        boolean success = authService.register(username, password, displayName);
        if (success) {
            logger.info("新用户注册: {}", username);
            return ResponseEntity.ok(Map.of("message", "注册成功", "username", username));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名已存在"));
        }
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @RequestAttribute("currentUser") String username,
            @RequestAttribute("currentRole") String role) {
        var user = authService.getUser(username);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
        }
        return ResponseEntity.ok(Map.of(
                "username", user.getUsername(),
                "role", user.getRole(),
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : username,
                "createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : "",
                "lastLoginAt", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : ""
        ));
    }

    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestAttribute("currentUser") String username,
            @RequestBody Map<String, String> request) {
        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");

        if (oldPassword == null || newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码长度至少 6 字符"));
        }

        boolean success = authService.changePassword(username, oldPassword, newPassword);
        if (success) {
            return ResponseEntity.ok(Map.of("message", "密码修改成功"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "旧密码错误"));
        }
    }

    /**
     * 管理员：列出所有用户
     */
    @GetMapping("/users")
    public ResponseEntity<?> listUsers(@RequestAttribute("currentRole") String role) {
        if (!"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
        }
        List<Map<String, Object>> users = authService.listUsers();
        return ResponseEntity.ok(users);
    }
}
