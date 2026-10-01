package edu.whut.clf.it;

import edu.whut.clf.claim.ClaimService;
import edu.whut.clf.claim.dto.ClaimDtos.ReviewRequest;
import edu.whut.clf.claim.dto.ClaimDtos.SubmitClaimRequest;
import edu.whut.clf.common.enums.UserStatus;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.post.PostService;
import edu.whut.clf.post.dto.PostDtos.CreatePostRequest;
import edu.whut.clf.post.dto.PostDtos.PostDetail;
import edu.whut.clf.user.UserMapper;
import edu.whut.clf.user.UserService;
import edu.whut.clf.user.dto.UserDtos.UpdateProfileRequest;
import edu.whut.clf.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 缺陷清零 B 批（前端 P1）后端配套回归：B3 校区空白不清空、B7 ClaimDetail.resolvedLostPostId 持久字段 +
 * 一 claim 一链接(R4) 409。本地默认跳过；CLF_IT=true + DB_NAME=campus_lost_found_test 运行。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CLF_IT", matches = "true")
class BatchBBackendIT {

    @Autowired UserMapper userMapper;
    @Autowired UserService userService;
    @Autowired PostService postService;
    @Autowired ClaimService claimService;

    private Long newUser(String nick) {
        User u = new User();
        u.setNickname(nick);
        u.setStatus(UserStatus.ACTIVE.name());
        u.setCampusVerificationStatus("UNVERIFIED");
        userMapper.insert(u);
        return u.getId();
    }

    private PostDetail lostBy(Long uid, String title, String category) {
        return postService.create(uid, new CreatePostRequest("LOST", title, category, "丢失物品求找回", "S", "Lib", null, List.of()));
    }

    private Long completedClaim(Long applicant, Long publisher, String foundTitle, String category) {
        PostDetail found = postService.create(publisher, new CreatePostRequest("FOUND", foundTitle, category, "捡到物品保管中", "S", "Lib", null, List.of()));
        var claim = claimService.submit(found.id(), applicant, new SubmitClaimRequest("特征一致可当面核验", List.of()));
        claimService.review(claim.id(), publisher, new ReviewRequest("ACCEPT", null));
        claimService.confirmHandover(claim.id(), applicant);
        claimService.confirmHandover(claim.id(), publisher);
        return claim.id();
    }

    // ==================== B3：校区空白不清空 ====================

    @Test
    void b3_blankCampus_keepsExisting() {
        Long a = newUser("b3-a");
        userService.updateProfile(a, new UpdateProfileRequest("昵称A", "南湖校区", null));
        assertEquals("南湖校区", userService.getProfile(a).campus());

        // 只改昵称，campus 传空白 → 不清空
        userService.updateProfile(a, new UpdateProfileRequest("昵称B", "   ", null));
        var p = userService.getProfile(a);
        assertEquals("昵称B", p.nickname());
        assertEquals("南湖校区", p.campus());

        // campus 传 null → 同样不动
        userService.updateProfile(a, new UpdateProfileRequest("昵称C", null, null));
        assertEquals("南湖校区", userService.getProfile(a).campus());
    }

    // ==================== B7：resolvedLostPostId 持久字段 + 一 claim 一链接 409 ====================

    @Test
    void b7_resolvedLostPostIdPersisted_andOneClaimOneLink() {
        Long a = newUser("b7-a");
        Long b = newUser("b7-b");
        PostDetail lost1 = lostBy(a, "丢失黑色钱包", "wallet");
        PostDetail lost2 = lostBy(a, "丢失另一只钱包", "wallet");
        Long claimId = completedClaim(a, b, "捡到黑色钱包", "wallet");

        // 关联前：DTO 字段为空
        assertNull(claimService.detail(claimId, a).resolvedLostPostId());

        claimService.resolveLost(claimId, a, lost1.id());
        // 关联后：持久字段回填
        assertEquals(lost1.id(), claimService.detail(claimId, a).resolvedLostPostId());

        // 同一 claim 再关联另一条寻物帖 → 409 RESOLVE_ALREADY_RESOLVED（R4 一 claim 一链接）
        BusinessException ex = assertThrows(BusinessException.class,
                () -> claimService.resolveLost(claimId, a, lost2.id()));
        assertEquals("RESOLVE_ALREADY_RESOLVED", ex.getErrorCode().name());

        // 重复关联同一条 → 幂等放行（不抛）
        assertDoesNotThrow(() -> claimService.resolveLost(claimId, a, lost1.id()));
    }
}
