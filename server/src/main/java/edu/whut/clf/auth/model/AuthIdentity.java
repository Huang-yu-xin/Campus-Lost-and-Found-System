package edu.whut.clf.auth.model;

import lombok.Data;

import java.time.LocalDateTime;

/** auth_identities 表实体。(provider, providerSubject) 唯一。 */
@Data
public class AuthIdentity {
    private Long id;
    private Long userId;
    private String provider;          // WECHAT / MOCK / CAMPUS
    private String providerSubject;   // 如微信 openid（不进入公开响应）
    private LocalDateTime createdAt;
}
