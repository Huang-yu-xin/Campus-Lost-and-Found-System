package edu.whut.clf.user.dto;

import jakarta.validation.constraints.Size;

public class UserDtos {

    public record UserProfileResponse(
            Long id, String nickname, String campus, Long avatarFileId,
            String status, String campusVerificationStatus) {}

    public record UpdateProfileRequest(
            @Size(max = 64) String nickname,
            @Size(max = 64) String campus,
            Long avatarFileId) {}
}
