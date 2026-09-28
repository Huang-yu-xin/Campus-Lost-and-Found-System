package edu.whut.clf.auth.model;

import lombok.Data;

import java.time.LocalDateTime;

/** admin_credentials 表实体。密码 BCrypt 散列。 */
@Data
public class AdminCredential {
    private Long adminUserId;
    private String username;
    private String passwordHash;
    private String securityStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
