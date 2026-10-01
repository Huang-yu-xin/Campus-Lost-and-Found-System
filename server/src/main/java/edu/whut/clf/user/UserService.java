package edu.whut.clf.user;

import edu.whut.clf.common.enums.FilePurpose;
import edu.whut.clf.common.enums.UserStatus;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import edu.whut.clf.file.FileService;
import edu.whut.clf.user.dto.UserDtos.*;
import edu.whut.clf.user.model.User;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final FileService fileService;

    public UserService(UserMapper userMapper, FileService fileService) {
        this.userMapper = userMapper;
        this.fileService = fileService;
    }

    public UserProfileResponse getProfile(Long userId) {
        User u = load(userId);
        return toResponse(u);
    }

    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User u = load(userId);
        if (req.nickname() != null && !req.nickname().isBlank()) {
            u.setNickname(req.nickname().trim());
        }
        if (req.campus() != null) {
            u.setCampus(req.campus());
        }
        if (req.avatarFileId() != null) {
            // A6(P1-B5)：头像必须是本人上传的文件（复用 PUBLIC_POST 用途，不新增枚举），
            // 否则 INVALID_EVIDENCE_FILE；杜绝设置他人/不存在文件为头像。
            // 校验后 markBound，使其不被 D13 孤儿清理误删（仅更换头像时旧文件成为可接受的留存）。
            fileService.requireOwnedFile(req.avatarFileId(), userId, FilePurpose.PUBLIC_POST);
            u.setAvatarFileId(req.avatarFileId());
            fileService.markBound(req.avatarFileId());
        }
        userMapper.updateProfile(u);
        return toResponse(u);
    }

    public User load(Long userId) {
        User u = userMapper.findById(userId);
        if (u == null) {
            throw BusinessException.of(ErrorCode.NOT_FOUND);
        }
        return u;
    }

    /** 被限制用户禁止写操作（发布/申请/留言/线索等调用此校验）。 */
    public void requireNotRestricted(Long userId) {
        User u = load(userId);
        if (UserStatus.RESTRICTED.name().equals(u.getStatus())
                || UserStatus.DISABLED.name().equals(u.getStatus())) {
            throw BusinessException.of(ErrorCode.USER_RESTRICTED);
        }
    }

    private UserProfileResponse toResponse(User u) {
        return new UserProfileResponse(u.getId(), u.getNickname(), u.getCampus(),
                u.getAvatarFileId(), u.getStatus(), u.getCampusVerificationStatus());
    }
}
