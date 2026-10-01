package edu.whut.clf.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** M1 认证相关 DTO 集合。 */
public class AuthDtos {

    public record WechatLoginRequest(@NotBlank String code, @Size(max = 64) String nickname) {}

    public record MockLoginRequest(@NotBlank @Size(max = 64) String testUser, @Size(max = 64) String nickname) {}

    public record AdminLoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record LoginResponse(String accessToken, Long userId, String role, String nickname) {}

    public record CampusCapabilitiesResponse(String provider, boolean verificationEnabled) {}
}
