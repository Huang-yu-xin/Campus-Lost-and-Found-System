package edu.whut.clf.it;

import edu.whut.clf.auth.SessionService;
import edu.whut.clf.claim.ClaimService;
import edu.whut.clf.claim.dto.ClaimDtos.ReviewRequest;
import edu.whut.clf.claim.dto.ClaimDtos.SubmitClaimRequest;
import edu.whut.clf.common.enums.FilePurpose;
import edu.whut.clf.common.enums.UserStatus;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.security.JwtService;
import edu.whut.clf.common.security.Principal;
import edu.whut.clf.dispute.DisputeService;
import edu.whut.clf.dispute.dto.DisputeDtos.RaiseDisputeRequest;
import edu.whut.clf.dispute.dto.DisputeDtos.ResolveRequest;
import edu.whut.clf.file.FileMapper;
import edu.whut.clf.file.model.StoredFile;
import edu.whut.clf.post.PostService;
import edu.whut.clf.post.dto.PostDtos.CreatePostRequest;
import edu.whut.clf.post.dto.PostDtos.PostDetail;
import edu.whut.clf.user.UserMapper;
import edu.whut.clf.user.dto.UserDtos.UpdateProfileRequest;
import edu.whut.clf.user.UserService;
import edu.whut.clf.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 缺陷清零 A 批（P0 + 后端 P1）回归：A1 multipart 配置绑定、A2 DTO 长度校验、A3 图片替换语义/去重/上限、
 * A4 cancel-handover 争议冻结、A5 resolve-lost 类型校验、A6 avatar 归属校验。
 * 本地默认跳过；CLF_IT=true + DB_NAME=campus_lost_found_test 运行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CLF_IT", matches = "true")
class BatchAFixesIT {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired SessionService sessionService;
    @Autowired UserMapper userMapper;
    @Autowired UserService userService;
    @Autowired PostService postService;
    @Autowired ClaimService claimService;
    @Autowired DisputeService disputeService;
    @Autowired FileMapper fileMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MultipartProperties multipartProperties;

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

    /** 直接在 files 表造一条本人上传、未绑定的 PUBLIC_POST 文件，返回 fileId。 */
    private Long newPublicFile(Long owner) {
        StoredFile f = new StoredFile();
        f.setOwnerId(owner);
        f.setPurpose(FilePurpose.PUBLIC_POST.name());
        f.setStorageKey("test/" + java.util.UUID.randomUUID().toString().replace("-", "") + ".jpg");
        f.setMimeType("image/jpeg");
        f.setSize(1024L);
        f.setVisibility("PUBLIC");
        f.setBound(false);
        fileMapper.insert(f);
        return f.getId();
    }

    private PostDetail lostBy(Long uid, String title, String category) {
        return postService.create(uid, new CreatePostRequest("LOST", title, category, "丢失物品求找回", "S", "Lib", null, List.of()));
    }

    private PostDetail foundBy(Long uid, String title, String category) {
        return postService.create(uid, new CreatePostRequest("FOUND", title, category, "捡到物品保管中", "S", "Lib", null, List.of()));
    }

    private Long completedClaim(Long applicant, Long publisher, String foundTitle, String category) {
        PostDetail found = foundBy(publisher, foundTitle, category);
        var claim = claimService.submit(found.id(), applicant, new SubmitClaimRequest("特征一致可当面核验", List.of()));
        claimService.review(claim.id(), publisher, new ReviewRequest("ACCEPT", null));
        claimService.confirmHandover(claim.id(), applicant);
        claimService.confirmHandover(claim.id(), publisher);
        return claim.id();
    }

    // ==================== A1：multipart 上限配置绑定 ====================

    @Test
    void a1_multipartConfigBound() {
        // 略大于业务 5MB，使 FileService 的 5MB 校验先行返回 FILE_TOO_LARGE
        assertEquals(6L, multipartProperties.getMaxFileSize().toMegabytes());
        assertEquals(10L, multipartProperties.getMaxRequestSize().toMegabytes());
    }

    // ==================== A2：DTO 长度校验 → 400 INVALID_ARGUMENT ====================

    @Test
    void a2_titleTooLong_400() throws Exception {
        Long a = newUser("a2-a");
        String longTitle = "标".repeat(129); // > 128
        String body = "{\"type\":\"LOST\",\"title\":\"" + longTitle + "\",\"category\":\"wallet\",\"publicDescription\":\"d\"}";
        mvc.perform(post("/posts").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void a2_categoryTooLong_400() throws Exception {
        Long a = newUser("a2-b");
        String longCat = "类".repeat(49); // > 48
        String body = "{\"type\":\"LOST\",\"title\":\"t\",\"category\":\"" + longCat + "\",\"publicDescription\":\"d\"}";
        mvc.perform(post("/posts").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    // ==================== A3：图片绑定替换语义 / 去重 / 上限 / 归属 ====================

    @Test
    void a3_reBindSameImage_noDuplicateRows() {
        Long a = newUser("a3-a");
        Long f1 = newPublicFile(a);
        PostDetail post = postService.create(a, new CreatePostRequest(
                "LOST", "丢失钱包", "wallet", "黑色钱包", "S", "Lib", null, List.of(f1)));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM post_images WHERE post_id=?", Integer.class, post.id()));
        // 重传同一张图（替换语义：先释放旧图再重建，不产生重复行）
        postService.update(post.id(), a, new edu.whut.clf.post.dto.PostDtos.UpdatePostRequest(
                null, null, null, null, null, null, List.of(f1, f1))); // 入参含重复，去重后仍 1 张
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM post_images WHERE post_id=?", Integer.class, post.id()));
    }

    @Test
    void a3_tooManyImages_400() throws Exception {
        Long a = newUser("a3-b");
        String ids = LongStream.rangeClosed(1, 1001).mapToObj(Long::toString).collect(Collectors.joining(","));
        String body = "{\"type\":\"LOST\",\"title\":\"t\",\"category\":\"c\",\"publicDescription\":\"d\",\"imageFileIds\":[" + ids + "]}";
        mvc.perform(post("/posts").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void a3_othersFile_400() throws Exception {
        Long a = newUser("a3-c");
        Long c = newUser("a3-d");
        Long cFile = newPublicFile(c);
        String body = "{\"type\":\"LOST\",\"title\":\"t\",\"category\":\"c\",\"publicDescription\":\"d\",\"imageFileIds\":[" + cFile + "]}";
        mvc.perform(post("/posts").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVIDENCE_FILE"));
    }

    // ==================== A4：OPEN 争议冻结 cancel-handover ====================

    @Test
    void a4_cancelHandover_frozenByOpenDispute_thenAllowedAfterResolve() {
        Long applicant = newUser("a4-app");
        Long publisher = newUser("a4-pub");
        PostDetail found = foundBy(publisher, "捡到书包", "bag");
        var claim = claimService.submit(found.id(), applicant, new SubmitClaimRequest("特征一致", List.of()));
        claimService.review(claim.id(), publisher, new ReviewRequest("ACCEPT", null)); // → WAITING_HANDOVER
        var dispute = disputeService.raise(claim.id(), applicant,
                new RaiseDisputeRequest("对方未到场", "多次爽约", List.of()));

        // OPEN 争议下取消交接 → 409 HANDOVER_PAUSED_BY_DISPUTE
        BusinessException ex = assertThrows(BusinessException.class,
                () -> claimService.cancelHandover(claim.id(), publisher, "放弃交接"));
        assertEquals("HANDOVER_PAUSED_BY_DISPUTE", ex.getErrorCode().name());

        // 管理员裁决 CONTINUE（争议不再 OPEN）后可取消
        Long admin = newUser("a4-admin");
        disputeService.assign(dispute.id(), admin);
        disputeService.resolve(dispute.id(), admin, new ResolveRequest("CONTINUE", "继续交接"));
        assertDoesNotThrow(() -> claimService.cancelHandover(claim.id(), publisher, "双方协商取消"));
    }

    // ==================== A5：resolve-lost 只能关联寻物帖 ====================

    @Test
    void a5_resolveFoundPost_400() throws Exception {
        Long a = newUser("a5-a");
        Long b = newUser("a5-b");
        PostDetail aFound = foundBy(a, "A捡到的伞", "umbrella"); // A 自己的 FOUND 帖（非 LOST）
        Long claimId = completedClaim(a, b, "捡到雨伞", "umbrella");

        mvc.perform(post("/claims/" + claimId + "/resolve-lost").header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lostPostId\":" + aFound.id() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    // ==================== A6：avatar 归属校验 ====================

    @Test
    void a6_avatarNonexistentFile_400() {
        Long a = newUser("a6-a");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateProfile(a, new UpdateProfileRequest(null, null, 999999999L)));
        assertEquals("INVALID_EVIDENCE_FILE", ex.getErrorCode().name());
    }

    @Test
    void a6_avatarOthersFile_400() {
        Long a = newUser("a6-b");
        Long c = newUser("a6-c");
        Long cFile = newPublicFile(c);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateProfile(a, new UpdateProfileRequest(null, null, cFile)));
        assertEquals("INVALID_EVIDENCE_FILE", ex.getErrorCode().name());
    }

    @Test
    void a6_avatarOwnFile_ok() {
        Long a = newUser("a6-d");
        Long f = newPublicFile(a);
        var resp = userService.updateProfile(a, new UpdateProfileRequest(null, null, f));
        assertEquals(f, resp.avatarFileId());
    }
}
