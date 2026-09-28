package edu.whut.clf.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** M1 认证相关 DTO 集合。 */
public class AuthDtos {

    public record WechatLoginRequest(@NotBlank String code, String nickname) {}

    public record MockLoginRequest(@NotBlank String testUser, String nickname) {}

    public record AdminLoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record LoginResponse(String accessToken, Long userId, String role, String nickname) {}

    public record CampusCapabilitiesResponse(String provider, boolean verificationEnabled) {}
}
