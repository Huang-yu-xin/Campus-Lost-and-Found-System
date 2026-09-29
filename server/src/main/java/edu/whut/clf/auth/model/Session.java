package edu.whut.clf.auth.model;

import lombok.Data;

import java.time.LocalDateTime;

/** sessions 表实体。仅存 token 的 SHA-256 摘要，不存明文令牌。 */
@Data
public class Session {
    private Long id;
    private Long userId;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
}
