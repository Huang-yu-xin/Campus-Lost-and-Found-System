package edu.whut.clf.user.model;

import lombok.Data;

import java.time.LocalDateTime;

/** users 表实体。 */
@Data
public class User {
    private Long id;
    private String nickname;
    private String campus;
    private Long avatarFileId;
    private String status;                     // UserStatus
    private String campusVerificationStatus;   // 本期恒 UNVERIFIED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
