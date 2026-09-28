package edu.whut.clf.user;

import edu.whut.clf.common.enums.UserStatus;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import edu.whut.clf.user.dto.UserDtos.*;
import edu.whut.clf.user.model.User;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
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
            u.setAvatarFileId(req.avatarFileId());
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
