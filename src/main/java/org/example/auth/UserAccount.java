package org.example.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户账户模型（存储在 Redis 中）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccount implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;    // BCrypt 加密后的密码
    private String role;        // admin / user
    private String displayName;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private boolean enabled;
}
