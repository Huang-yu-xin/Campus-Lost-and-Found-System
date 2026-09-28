package edu.whut.clf.user.dto;

public class UserDtos {

    public record UserProfileResponse(
            Long id, String nickname, String campus, Long avatarFileId,
            String status, String campusVerificationStatus) {}

    public record UpdateProfileRequest(String nickname, String campus, Long avatarFileId) {}
}
