package edu.whut.clf.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.clf.claim.ClaimService;
import edu.whut.clf.claim.dto.ClaimDtos.SubmitClaimRequest;
import edu.whut.clf.common.security.JwtService;
import edu.whut.clf.common.security.Principal;
import edu.whut.clf.auth.SessionService;
import edu.whut.clf.common.enums.UserStatus;
import edu.whut.clf.lead.LeadService;
import edu.whut.clf.lead.dto.LeadDtos.SubmitLeadRequest;
import edu.whut.clf.lead.dto.LeadDtos.LeadItem;
import edu.whut.clf.post.PostService;
import edu.whut.clf.post.dto.PostDtos.CreatePostRequest;
import edu.whut.clf.post.dto.PostDtos.PostDetail;
import edu.whut.clf.user.UserMapper;
import edu.whut.clf.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API 鉴权/异常关键用例集成测试（真实 DB，MockMvc 全拦截器链）。
 * 本地默认跳过；CI 或本地 CLF_IT=true + DB_NAME=campus_lost_found_test 运行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CLF_IT", matches = "true")
class ApiAuthzIT {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired SessionService sessionService;
    @Autowired UserMapper userMapper;
    @Autowired PostService postService;
    @Autowired ClaimService claimService;
    @Autowired LeadService leadService;
    @Autowired ObjectMapper om;

    private Long newUser(String nick) {
        User u = new User();
        u.setNickname(nick);
        u.setStatus(UserStatus.ACTIVE.name());
        u.setCampusVerificationStatus("UNVERIFIED");
        userMapper.insert(u);
        return u.getId();
    }

    private String token(Long userId, String role) {
        String t = jwtService.issueAccessToken(userId, role);
        sessionService.create(userId, t);
        return t;
    }

    private String bearer(Long userId) { return "Bearer " + token(userId, Principal.ROLE_USER); }
    private String adminBearer(Long userId) { return "Bearer " + token(userId, Principal.ROLE_ADMIN); }

    private PostDetail foundBy(Long uid, String title) {
        return postService.create(uid, new CreatePostRequest(
                "FOUND", title, "wallet", "desc", "S", "Lib", null, List.of()));
    }

    private PostDetail lostBy(Long uid, String title) {
        return postService.create(uid, new CreatePostRequest(
                "LOST", title, "wallet", "desc", "S", "Lib", null, List.of()));
    }

    @Test
    void adminEndpoint_forbiddenForUser() throws Exception { // TC-AUTH-01
        Long u = newUser("authz-user");
        mvc.perform(get("/admin/users").header("Authorization", bearer(u)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unauthenticated_write_401() throws Exception { // TC-AUTH-02 相关：无有效会话不可写
        mvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void selfClaim_forbidden() throws Exception { // TC-CLAIM-01
        Long b = newUser("authz-self");
        PostDetail post = foundBy(b, "self-claim-post");
        mvc.perform(post("/posts/" + post.id() + "/claims")
                        .header("Authorization", bearer(b))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"mine\",\"evidenceFileIds\":[]}"))
                .andExpect(jsonPath("$.code").value("SELF_CLAIM_FORBIDDEN"));
    }

    @Test
    void editOthersPost_forbidden_and_lockedAfterClaim() throws Exception { // TC-POST-02
        Long a = newUser("authz-a");
        Long b = newUser("authz-b");
        PostDetail post = foundBy(a, "edit-lock-post");
        // B 编辑 A 的发布 → 403
        mvc.perform(patch("/posts/" + post.id())
                        .header("Authorization", bearer(b))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hack\"}"))
                .andExpect(status().isForbidden());
        // B 提交有效申请后，A 改核心字段 → POST_EDIT_LOCKED
        claimService.submit(post.id(), b, new SubmitClaimRequest("has card", List.of()));
        mvc.perform(patch("/posts/" + post.id())
                        .header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"changed\"}"))
                .andExpect(jsonPath("$.code").value("POST_EDIT_LOCKED"));
    }

    @Test
    void strangerClaim_404() throws Exception { // TC-FILE-01 / NFR-SEC-01 邻近：陌生人读申请 404
        Long a = newUser("authz-owner");
        Long b = newUser("authz-applicant");
        Long c = newUser("authz-stranger");
        PostDetail post = foundBy(a, "stranger-claim");
        var claim = claimService.submit(post.id(), b, new SubmitClaimRequest("mine", List.of()));
        mvc.perform(get("/claims/" + claim.id()).header("Authorization", bearer(c)))
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));
    }

    @Test
    void strangerLead_404() throws Exception { // TC-LEAD-01：第三方读他人线索 404
        Long a = newUser("lead-owner");      // 寻物发布者
        Long b = newUser("lead-reporter");   // 线索提交者
        Long c = newUser("lead-stranger");   // 无关第三方
        PostDetail lost = lostBy(a, "lost-item");
        LeadItem lead = leadService.submit(lost.id(), b, new SubmitLeadRequest("saw it near gate", List.of()));
        // 第三方读线索 → 404
        mvc.perform(get("/leads/" + lead.id()).header("Authorization", bearer(c)))
                .andExpect(jsonPath("$.code").value("LEAD_NOT_FOUND"));
        // 提交者与发布者可读 → OK
        mvc.perform(get("/leads/" + lead.id()).header("Authorization", bearer(b)))
                .andExpect(jsonPath("$.code").value("OK"));
        mvc.perform(get("/leads/" + lead.id()).header("Authorization", bearer(a)))
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void restrictedUser_write_rejected() throws Exception { // TC-ADMIN-02
        Long u = newUser("authz-restricted");
        userMapper.updateStatus(u, UserStatus.RESTRICTED.name());
        mvc.perform(post("/posts")
                        .header("Authorization", bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LOST\",\"title\":\"t\",\"category\":\"c\",\"publicDescription\":\"d\",\"imageFileIds\":[]}"))
                .andExpect(jsonPath("$.code").value("USER_RESTRICTED"));
    }

    @Test
    void unknownRoute_404_and_badJson_400() throws Exception { // B5
        mvc.perform(get("/nonexistent-xyz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        Long u = newUser("authz-json");
        mvc.perform(post("/posts").header("Authorization", bearer(u))
                        .contentType(MediaType.APPLICATION_JSON).content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void logout_revokesSession() throws Exception { // B3
        Long u = newUser("authz-logout");
        String t = token(u, Principal.ROLE_USER);
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk());
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + t))
                .andExpect(status().isUnauthorized());
    }
}
