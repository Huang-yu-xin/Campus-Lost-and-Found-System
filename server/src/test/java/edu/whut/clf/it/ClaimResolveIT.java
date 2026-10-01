package edu.whut.clf.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.clf.claim.ClaimService;
import edu.whut.clf.claim.dto.ClaimDtos.ReviewRequest;
import edu.whut.clf.claim.dto.ClaimDtos.SubmitClaimRequest;
import edu.whut.clf.common.enums.UserStatus;
import edu.whut.clf.common.security.JwtService;
import edu.whut.clf.common.security.Principal;
import edu.whut.clf.auth.SessionService;
import edu.whut.clf.post.PostMapper;
import edu.whut.clf.post.PostService;
import edu.whut.clf.post.dto.PostDtos.CreatePostRequest;
import edu.whut.clf.post.dto.PostDtos.PostDetail;
import edu.whut.clf.post.model.Post;
import edu.whut.clf.user.UserMapper;
import edu.whut.clf.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * V4 闭环：认领完成 → 寻物帖关联（resolve-lost）端到端集成测试（真实 DB，MockMvc 全拦截器链）。
 * 本地默认跳过；CLF_IT=true + DB_NAME=campus_lost_found_test 运行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CLF_IT", matches = "true")
class ClaimResolveIT {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired SessionService sessionService;
    @Autowired UserMapper userMapper;
    @Autowired PostService postService;
    @Autowired ClaimService claimService;
    @Autowired PostMapper postMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper om;

    private Long newUser(String nick) {
        User u = new User();
        u.setNickname(nick);
        u.setStatus(UserStatus.ACTIVE.name());
        u.setCampusVerificationStatus("UNVERIFIED");
        userMapper.insert(u);
        return u.getId();
    }

    // 每个用户仅签发一次 token（复用，像真实客户端），避免同秒 iat 令牌重复触发会话唯一键冲突。
    private final Map<Long, String> tokenCache = new HashMap<>();

    private String bearer(Long userId) {
        String t = tokenCache.computeIfAbsent(userId, uid -> {
            String token = jwtService.issueAccessToken(uid, Principal.ROLE_USER);
            sessionService.create(uid, token);
            return token;
        });
        return "Bearer " + t;
    }

    private PostDetail foundBy(Long uid, String title, String category) {
        return postService.create(uid, new CreatePostRequest("FOUND", title, category, "捡到物品保管中", "S", "Lib", null, List.of()));
    }

    private PostDetail lostBy(Long uid, String title, String category) {
        return postService.create(uid, new CreatePostRequest("LOST", title, category, "丢失物品求找回", "S", "Lib", null, List.of()));
    }

    /** 跑完一次完整认领直到 COMPLETED，返回 claimId。 */
    private Long completedClaim(Long applicant, Long publisher, String foundTitle, String category) {
        PostDetail found = foundBy(publisher, foundTitle, category);
        var claim = claimService.submit(found.id(), applicant, new SubmitClaimRequest("特征一致可当面核验", List.of()));
        claimService.review(claim.id(), publisher, new ReviewRequest("ACCEPT", null));
        claimService.confirmHandover(claim.id(), applicant);
        claimService.confirmHandover(claim.id(), publisher);
        return claim.id();
    }

    private String resolveBody(Long lostPostId) {
        return "{\"lostPostId\":" + lostPostId + "}";
    }

    // ==================== 正常流 ====================

    @Test
    void happyPath_candidatesThenResolve() throws Exception {
        Long a = newUser("rl-a");      // 申请人（失主）
        Long b = newUser("rl-b");      // 招领发布者
        PostDetail lost = lostBy(a, "丢失黑色钱包", "wallet");
        Long claimId = completedClaim(a, b, "捡到黑色钱包", "wallet");

        // 候选包含 A 的 LOST 帖
        mvc.perform(get("/claims/" + claimId + "/resolved-candidates").header("Authorization", bearer(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items[*].id", hasItem(lost.id().intValue())));

        // 关联
        mvc.perform(post("/claims/" + claimId + "/resolve-lost").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content(resolveBody(lost.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        // LOST 帖闭环：COMPLETED + resolved_by_claim_id + closed_at
        Post updated = postMapper.findById(lost.id());
        assertEquals("COMPLETED", updated.getStatus());
        assertEquals(claimId, updated.getResolvedByClaimId());
        assertNotNull(updated.getClosedAt());

        // 审计 LOST_RESOLVED
        Long auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='LOST_RESOLVED' AND target_type='POST' AND target_id=? AND actor_id=?",
                Long.class, lost.id(), a);
        assertEquals(1L, auditCount);

        // 寻物帖已闭环后不再产出匹配
        mvc.perform(get("/posts/" + lost.id() + "/matches").header("Authorization", bearer(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void duplicateSameClaim_idempotentOk() throws Exception {
        Long a = newUser("rl-dup-a");
        Long b = newUser("rl-dup-b");
        PostDetail lost = lostBy(a, "丢失蓝色雨伞", "umbrella");
        Long claimId = completedClaim(a, b, "捡到蓝色雨伞", "umbrella");

        mvc.perform(post("/claims/" + claimId + "/resolve-lost").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content(resolveBody(lost.id())))
                .andExpect(jsonPath("$.code").value("OK"));
        // 重复同一 claim 关联 → 幂等 OK
        mvc.perform(post("/claims/" + claimId + "/resolve-lost").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content(resolveBody(lost.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    // ==================== 负例 ====================

    @Test
    void nonApplicant_candidatesAndResolve_404() throws Exception {
        Long a = newUser("rl-np-a");
        Long b = newUser("rl-np-b");
        Long c = newUser("rl-np-c");   // 无关第三方
        PostDetail lost = lostBy(a, "丢失校园卡", "campus-card");
        Long claimId = completedClaim(a, b, "捡到校园卡", "campus-card");

        mvc.perform(get("/claims/" + claimId + "/resolved-candidates").header("Authorization", bearer(c)))
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));
        mvc.perform(post("/claims/" + claimId + "/resolve-lost").header("Authorization", bearer(c))
                        .contentType(MediaType.APPLICATION_JSON).content(resolveBody(lost.id())))
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));
    }

    @Test
    void notOwnerPost_403() throws Exception {
        Long a = newUser("rl-no-a");
        Long b = newUser("rl-no-b");
        Long c = newUser("rl-no-c");
        PostDetail othersLost = lostBy(c, "丢失耳机", "earphones"); // 属于 C，非申请人 A
        Long claimId = completedClaim(a, b, "捡到耳机", "earphones");

        mvc.perform(post("/claims/" + claimId + "/resolve-lost").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content(resolveBody(othersLost.id())))
                .andExpect(jsonPath("$.code").value("RESOLVE_NOT_OWNER"));
    }

    @Test
    void claimNotCompleted_409() throws Exception {
        Long a = newUser("rl-nc-a");
        Long b = newUser("rl-nc-b");
        PostDetail lost = lostBy(a, "丢失水杯", "cup");
        PostDetail found = foundBy(b, "捡到水杯", "cup");
        var claim = claimService.submit(found.id(), a, new SubmitClaimRequest("特征一致", List.of())); // 仅 PENDING

        mvc.perform(get("/claims/" + claim.id() + "/resolved-candidates").header("Authorization", bearer(a)))
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_COMPLETED"));
        mvc.perform(post("/claims/" + claim.id() + "/resolve-lost").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content(resolveBody(lost.id())))
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_COMPLETED"));
    }

    @Test
    void alreadyResolvedByOtherClaim_409() throws Exception {
        Long a = newUser("rl-ar-a");
        Long b = newUser("rl-ar-b");
        PostDetail lost = lostBy(a, "丢失手机", "phone");
        Long claim1 = completedClaim(a, b, "捡到手机一号", "phone");
        Long claim2 = completedClaim(a, b, "捡到手机二号", "phone");

        // claim1 先关联成功
        mvc.perform(post("/claims/" + claim1 + "/resolve-lost").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content(resolveBody(lost.id())))
                .andExpect(jsonPath("$.code").value("OK"));
        // claim2 再关联同一 LOST → 409
        mvc.perform(post("/claims/" + claim2 + "/resolve-lost").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content(resolveBody(lost.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOLVE_ALREADY_RESOLVED"));
    }

    @Test
    void categoryMismatch_400() throws Exception {
        Long a = newUser("rl-cm-a");
        Long b = newUser("rl-cm-b");
        PostDetail lost = lostBy(a, "丢失一串钥匙", "keys");
        Long claimId = completedClaim(a, b, "捡到一个钱包", "wallet"); // 类别不同

        mvc.perform(post("/claims/" + claimId + "/resolve-lost").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content(resolveBody(lost.id())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESOLVE_CATEGORY_MISMATCH"));
    }

    @Test
    void targetPostWithdrawn_409() throws Exception {
        Long a = newUser("rl-wd-a");
        Long b = newUser("rl-wd-b");
        PostDetail lost = lostBy(a, "丢失充电宝", "powerbank");
        Long claimId = completedClaim(a, b, "捡到充电宝", "powerbank");
        postService.withdraw(lost.id(), a); // LOST 帖撤回 → 非 ACTIVE

        mvc.perform(post("/claims/" + claimId + "/resolve-lost").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content(resolveBody(lost.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOLVE_ALREADY_RESOLVED"));
    }
}
