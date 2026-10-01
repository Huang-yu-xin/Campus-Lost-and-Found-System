package edu.whut.clf.it;

import edu.whut.clf.claim.ClaimService;
import edu.whut.clf.claim.dto.ClaimDtos.SubmitClaimRequest;
import edu.whut.clf.common.enums.UserStatus;
import edu.whut.clf.lead.LeadService;
import edu.whut.clf.lead.dto.LeadDtos.LeadItem;
import edu.whut.clf.lead.dto.LeadDtos.SubmitLeadRequest;
import edu.whut.clf.post.PostService;
import edu.whut.clf.post.dto.PostDtos.CreatePostRequest;
import edu.whut.clf.post.dto.PostDtos.PostDetail;
import edu.whut.clf.user.UserMapper;
import edu.whut.clf.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 缺陷清零 E 批前端配套后端回归：E18 LeadItem.owner 填充、E28 ClaimSummary.postTitle 联表。
 * CLF_IT=true + DB_NAME=campus_lost_found_test 运行。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CLF_IT", matches = "true")
class BatchEBackendIT {

    @Autowired UserMapper userMapper;
    @Autowired PostService postService;
    @Autowired LeadService leadService;
    @Autowired ClaimService claimService;

    private Long newUser(String nick) {
        User u = new User();
        u.setNickname(nick);
        u.setStatus(UserStatus.ACTIVE.name());
        u.setCampusVerificationStatus("UNVERIFIED");
        userMapper.insert(u);
        return u.getId();
    }

    @Test
    void e18_leadOwnerFlag() {
        Long publisher = newUser("e18-pub");
        Long reporter = newUser("e18-rep");
        PostDetail lost = postService.create(publisher,
                new CreatePostRequest("LOST", "丢失手表e18", "watch", "丢了", "S", "L", null, List.of()));
        LeadItem submitted = leadService.submit(lost.id(), reporter, new SubmitLeadRequest("见过", List.of()));
        // 发布者查看 → owner=true
        assertTrue(leadService.get(submitted.id(), publisher).owner());
        // 报告人查看 → owner=false
        assertFalse(leadService.get(submitted.id(), reporter).owner());
    }

    @Test
    void e28_myClaimsHasPostTitle() {
        Long applicant = newUser("e28-app");
        Long publisher = newUser("e28-pub");
        PostDetail found = postService.create(publisher,
                new CreatePostRequest("FOUND", "捡到钱包e28", "wallet", "捡到了", "S", "L", null, List.of()));
        claimService.submit(found.id(), applicant, new SubmitClaimRequest("特征一致", List.of()));
        var page = claimService.myClaims(applicant, 1, 20);
        assertFalse(page.items().isEmpty());
        assertEquals("捡到钱包e28", page.items().get(0).postTitle());
    }
}
