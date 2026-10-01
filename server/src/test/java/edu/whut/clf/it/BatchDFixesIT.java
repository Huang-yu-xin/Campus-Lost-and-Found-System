package edu.whut.clf.it;

import edu.whut.clf.auth.SessionService;
import edu.whut.clf.auth.dto.AuthDtos.AdminLoginRequest;
import edu.whut.clf.auth.AuthService;
import edu.whut.clf.backup.BackupService;
import edu.whut.clf.backup.model.BackupRecord;
import edu.whut.clf.backup.BackupRecordMapper;
import edu.whut.clf.claim.ClaimService;
import edu.whut.clf.claim.dto.ClaimDtos.ReviewRequest;
import edu.whut.clf.claim.dto.ClaimDtos.SubmitClaimRequest;
import edu.whut.clf.common.enums.FilePurpose;
import edu.whut.clf.common.enums.UserStatus;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.security.JwtService;
import edu.whut.clf.common.security.Principal;
import edu.whut.clf.file.FileService;
import edu.whut.clf.file.model.StoredFile;
import edu.whut.clf.lead.LeadService;
import edu.whut.clf.lead.dto.LeadDtos.SubmitLeadRequest;
import edu.whut.clf.lead.dto.LeadDtos.LeadItem;
import edu.whut.clf.post.PostService;
import edu.whut.clf.post.dto.PostDtos.CreatePostRequest;
import edu.whut.clf.post.dto.PostDtos.PostDetail;
import edu.whut.clf.auth.SessionMapper;
import edu.whut.clf.user.UserMapper;
import edu.whut.clf.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 缺陷清零 D 批（后端 P2）关键分支回归：D4 未绑定公开文件访问、D5 管理员防爆破、D7 争议下禁下架、
 * D8 事件时间校验、D9 线索状态白名单、D10 非参与方 404、D11/D12 审计补点、D13/D14 定时清理逻辑、D17 撤回竞态复核。
 * CLF_IT=true + DB_NAME=campus_lost_found_test 运行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CLF_IT", matches = "true")
class BatchDFixesIT {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired SessionService sessionService;
    @Autowired UserMapper userMapper;
    @Autowired PostService postService;
    @Autowired ClaimService claimService;
    @Autowired LeadService leadService;
    @Autowired FileService fileService;
    @Autowired AuthService authService;
    @Autowired BackupService backupService;
    @Autowired BackupRecordMapper backupMapper;
    @Autowired SessionMapper sessionMapper;
    @Autowired JdbcTemplate jdbc;

    private final Map<Long, String> tokenCache = new HashMap<>();

    private Long newUser(String nick) {
        User u = new User();
        u.setNickname(nick);
        u.setStatus(UserStatus.ACTIVE.name());
        u.setCampusVerificationStatus("UNVERIFIED");
        userMapper.insert(u);
        return u.getId();
    }

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

    private Long waitingHandoverClaim(Long applicant, Long publisher, String cat) {
        PostDetail found = foundBy(publisher, "捡到" + cat, cat);
        var claim = claimService.submit(found.id(), applicant, new SubmitClaimRequest("特征一致", List.of()));
        claimService.review(claim.id(), publisher, new ReviewRequest("ACCEPT", null));
        return claim.id();
    }

    // ==================== D4：未绑定 PUBLIC 文件仅 owner/admin 可见 ====================

    @Test
    void d4_publicUnboundFile_onlyOwnerCanRead() throws Exception {
        Long a = newUser("d4-a");
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2, 3, 4, 5, 6, 7, 8};
        var mf = new MockMultipartFile("file", "x.jpg", "image/jpeg", jpeg);
        StoredFile f = fileService.upload(a, mf, "PUBLIC_POST"); // bound=0

        // 匿名访问未绑定公开文件 → 404（D4）
        mvc.perform(get("/files/" + f.getId())).andExpect(status().isNotFound());
        // owner 访问 → 200
        mvc.perform(get("/files/" + f.getId()).header("Authorization", bearer(a))).andExpect(status().isOk());
        // 单独设置 bound 不能替代真实业务关联。
        fileService.markBound(f.getId());
        mvc.perform(get("/files/" + f.getId())).andExpect(status().isNotFound());
        fileService.markUnbound(f.getId());
        postService.create(a, new CreatePostRequest("FOUND", "public photo", "wallet", "public", "S", "Lib", null, List.of(f.getId())));
        mvc.perform(get("/files/" + f.getId())).andExpect(status().isOk());
    }

    // ==================== D5：管理员登录防爆破 ====================

    @Test
    void d5_adminLogin_lockAfter5Failures() {
        String user = "nope-" + System.nanoTime();
        for (int i = 0; i < 5; i++) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.adminLogin(new AdminLoginRequest(user, "wrong")));
            assertEquals("ADMIN_LOGIN_FAILED", ex.getErrorCode().name());
        }
        // 第 6 次：锁定窗口内 → 429 RATE_LIMITED
        BusinessException locked = assertThrows(BusinessException.class,
                () -> authService.adminLogin(new AdminLoginRequest(user, "wrong")));
        assertEquals("RATE_LIMITED", locked.getErrorCode().name());
    }

    // ==================== D7：目标帖有 OPEN 争议时禁止下架 ====================

    @Test
    void d7_removePostWithOpenDispute_409() throws Exception {
        Long applicant = newUser("d7-app");
        Long publisher = newUser("d7-pub");
        Long admin = newUser("d7-admin");
        PostDetail found = foundBy(publisher, "捡到钱包d7", "wallet");
        var claim = claimService.submit(found.id(), applicant, new SubmitClaimRequest("特征一致", List.of()));
        claimService.review(claim.id(), publisher, new ReviewRequest("ACCEPT", null));
        // applicant 发起争议
        mvc.perform(post("/claims/" + claim.id() + "/disputes").header("Authorization", bearer(applicant))
                .contentType("application/json").content("{\"reason\":\"对方未到场\"}"))
                .andExpect(status().isOk());
        // 管理员下架该 FOUND 帖 → 409（存在 OPEN 争议）
        String adminToken = "Bearer " + jwtService.issueAccessToken(admin, Principal.ROLE_ADMIN);
        sessionService.create(admin, adminToken.substring(7));
        mvc.perform(post("/admin/posts/" + found.id() + "/remove").header("Authorization", adminToken)
                .contentType("application/json").content("{\"reason\":\"测试下架\"}"))
                .andExpect(status().isConflict());
    }

    // ==================== D8：事件时间校验 ====================

    @Test
    void d8_futureEventTime_400() throws Exception {
        Long a = newUser("d8-a");
        String future = LocalDateTime.now(java.time.Clock.systemUTC()).plusDays(3).withNano(0).toString();
        String body = "{\"type\":\"LOST\",\"title\":\"t\",\"category\":\"c\",\"publicDescription\":\"d\",\"eventTime\":\"" + future + "\"}";
        mvc.perform(post("/posts").header("Authorization", bearer(a))
                .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void d8_tooOldEventTime_400() {
        Long a = newUser("d8-b");
        BusinessException ex = assertThrows(BusinessException.class, () -> postService.create(a,
                new CreatePostRequest("LOST", "t", "c", "d", "S", "L", LocalDateTime.of(1990, 1, 1, 0, 0), List.of())));
        assertEquals("INVALID_ARGUMENT", ex.getErrorCode().name());
    }

    // ==================== D9：线索状态白名单 ====================

    @Test
    void d9_illegalLeadTransition_409() {
        Long a = newUser("d9-owner");
        Long b = newUser("d9-reporter");
        PostDetail lost = lostBy(a, "丢失伞d9", "umbrella");
        LeadItem lead = leadService.submit(lost.id(), b, new SubmitLeadRequest("在图书馆见到", List.of()));
        leadService.review(lead.id(), a, "CLOSED"); // SUBMITTED→CLOSED 合法
        // CLOSED→HELPFUL 非法 → 409
        BusinessException ex = assertThrows(BusinessException.class,
                () -> leadService.review(lead.id(), a, "HELPFUL"));
        assertEquals("CONFLICT", ex.getErrorCode().name());
    }

    // ==================== D10：非参与方 confirm/cancel → 404 ====================

    @Test
    void d10_nonParticipantHandover_404() throws Exception {
        Long applicant = newUser("d10-app");
        Long publisher = newUser("d10-pub");
        Long outsider = newUser("d10-out");
        Long claimId = waitingHandoverClaim(applicant, publisher, "bag10");
        mvc.perform(post("/claims/" + claimId + "/confirmations").header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HANDOVER_NOT_PARTICIPANT"));
        mvc.perform(post("/claims/" + claimId + "/cancel-handover").header("Authorization", bearer(outsider))
                .contentType("application/json").content("{\"reason\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HANDOVER_NOT_PARTICIPANT"));
    }

    // ==================== D11/D12：审计补点 ====================

    @Test
    void d11_cancelHandoverAudited() {
        Long applicant = newUser("d11-app");
        Long publisher = newUser("d11-pub");
        Long claimId = waitingHandoverClaim(applicant, publisher, "cup11");
        claimService.cancelHandover(claimId, publisher, "双方协商取消");
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='HANDOVER_CANCELLED' AND target_type='CLAIM' AND target_id=?",
                Long.class, claimId);
        assertEquals(1L, n);
    }

    @Test
    void d12_markFoundAndLeadReviewAudited() {
        Long a = newUser("d12-a");
        PostDetail lost = lostBy(a, "丢失卡d12", "card");
        postService.markFound(lost.id(), a);
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='LOST_MARK_FOUND' AND target_id=?", Long.class, lost.id()));

        Long b = newUser("d12-b");
        PostDetail lost2 = lostBy(a, "丢失书d12", "book");
        LeadItem lead = leadService.submit(lost2.id(), b, new SubmitLeadRequest("见过", List.of()));
        leadService.review(lead.id(), a, "HELPFUL");
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='LEAD_REVIEW' AND target_id=?", Long.class, lead.id()));
    }

    // ==================== D13/D14：定时清理逻辑（函数级） ====================

    @Test
    void d13_orphanFileCleanup() {
        Long a = newUser("d13-a");
        // 旧的未绑定文件（应删）
        Long oldOrphan = insertFile(a, false, LocalDateTime.now(java.time.Clock.systemUTC()).minusHours(48));
        // 新的未绑定文件（应留）
        Long freshOrphan = insertFile(a, false, LocalDateTime.now(java.time.Clock.systemUTC()));
        // 旧的已绑定文件（应留）
        Long oldBound = insertFile(a, true, LocalDateTime.now(java.time.Clock.systemUTC()).minusHours(48));

        fileService.cleanupOrphanFiles();

        assertEquals(0, countFile(oldOrphan));
        assertEquals(1, countFile(freshOrphan));
        assertEquals(1, countFile(oldBound));
    }

    @Test
    void d13_backupZombieFailed() {
        Long admin = newUser("d13-bk");
        jdbc.update("INSERT INTO backup_records (initiated_by, started_at, status) VALUES (?, ?, 'RUNNING')",
                admin, LocalDateTime.now(java.time.Clock.systemUTC()).minusHours(2));
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM backup_records", Long.class);
        backupService.failTimedOutRunning();
        String status = jdbc.queryForObject("SELECT status FROM backup_records WHERE id=?", String.class, id);
        assertEquals("FAILED", status);
    }

    @Test
    void d14_staleSessionPurge() {
        Long a = newUser("d14-a");
        // 已撤销 + 创建超 30 天 → 应删
        jdbc.update("INSERT INTO sessions (user_id, token_hash, expires_at, revoked_at, created_at) VALUES (?,?,?,?,?)",
                a, "stale-" + System.nanoTime(), LocalDateTime.now(java.time.Clock.systemUTC()).minusDays(29),
                LocalDateTime.now(java.time.Clock.systemUTC()).minusDays(31), LocalDateTime.now(java.time.Clock.systemUTC()).minusDays(31));
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM sessions WHERE user_id=?", Long.class, a);
        int removed = sessionService.purgeStaleSessions();
        long after = jdbc.queryForObject("SELECT COUNT(*) FROM sessions WHERE user_id=?", Long.class, a);
        assertTrue(removed >= 1);
        assertEquals(before - 1, after);
    }

    // ==================== D17：撤回与有效申请竞态复核 ====================

    @Test
    void d17_withdrawWithActiveClaim_409() {
        Long applicant = newUser("d17-app");
        Long publisher = newUser("d17-pub");
        PostDetail found = foundBy(publisher, "捡到耳机d17", "earphones");
        claimService.submit(found.id(), applicant, new SubmitClaimRequest("特征一致", List.of())); // PENDING
        BusinessException ex = assertThrows(BusinessException.class,
                () -> postService.withdraw(found.id(), publisher));
        assertEquals("CONFLICT", ex.getErrorCode().name());
    }

    // ---- helpers ----
    private Long insertFile(Long owner, boolean bound, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO files (owner_id, purpose, storage_key, mime_type, size, visibility, bound, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                owner, "PUBLIC_POST", "t/" + System.nanoTime() + ".jpg", "image/jpeg", 10L, "PUBLIC",
                bound ? 1 : 0, createdAt);
        return jdbc.queryForObject("SELECT MAX(id) FROM files WHERE owner_id=?", Long.class, owner);
    }

    private int countFile(Long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE id=?", Integer.class, id);
    }
}
