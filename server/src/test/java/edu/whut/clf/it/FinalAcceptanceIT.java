package edu.whut.clf.it;

import edu.whut.clf.auth.*;
import edu.whut.clf.claim.*;
import edu.whut.clf.claim.dto.ClaimDtos.*;
import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.security.*;
import edu.whut.clf.dispute.*;
import edu.whut.clf.dispute.dto.DisputeDtos.*;
import edu.whut.clf.file.*;
import edu.whut.clf.lead.*;
import edu.whut.clf.lead.dto.LeadDtos.SubmitLeadRequest;
import edu.whut.clf.post.*;
import edu.whut.clf.post.dto.PostDtos.*;
import edu.whut.clf.user.*;
import edu.whut.clf.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 独立终审反例回归：真实MySQL锁交错，不把任意异常伪装为业务冲突。 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CLF_IT", matches = "true")
class FinalAcceptanceIT {
    @Autowired ClaimService claims;
    @Autowired PostService posts;
    @Autowired LeadService leads;
    @Autowired DisputeService disputes;
    @Autowired UserMapper users;
    @Autowired FileService files;
    @Autowired FileMapper fileMapper;
    @Autowired SessionService sessions;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired MockMvc mvc;
    @Autowired AppProperties props;

    long user() {
        User u = new User(); u.setNickname("final-" + UUID.randomUUID());
        u.setStatus("ACTIVE"); u.setCampusVerificationStatus("UNVERIFIED"); users.insert(u); return u.getId();
    }
    String token(long id, String role) {
        String t = jwt.issueAccessToken(id, role); sessions.create(id, t); return "Bearer " + t;
    }
    long createPost(long owner, String type) {
        return posts.create(owner, new CreatePostRequest(type, "final wallet", "wallet", "public", "S", "Lib", null, List.of())).id();
    }
    record Waiting(long post, long claim, long applicant, long publisher) {}
    Waiting waiting() {
        long a=user(), b=user(), p=createPost(b,"FOUND");
        long c=claims.submit(p,a,new SubmitClaimRequest("private proof",List.of())).id();
        claims.review(c,b,new ReviewRequest("ACCEPT",null)); return new Waiting(p,c,a,b);
    }
    int run(Runnable action) {
        try { action.run(); return 200; }
        catch (BusinessException e) { assertEquals(409,e.getErrorCode().httpStatus().value()); return 409; }
    }
    List<Integer> race(String table, long id, Runnable first, Runnable second) throws Exception {
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try (var connection=dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement=connection.prepareStatement("SELECT id FROM " + table + " WHERE id=? FOR UPDATE")) {
                statement.setLong(1,id); try(var rs=statement.executeQuery()) { assertTrue(rs.next()); }
            }
            var a=pool.submit(() -> { start.await(); return run(first); });
            var b=pool.submit(() -> { start.await(); return run(second); });
            start.countDown(); Thread.sleep(400); connection.commit();
            return List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    @Test void simultaneousConfirmationsAlwaysComplete() throws Exception {
        var w=waiting();
        assertEquals(List.of(200,200),race("claims",w.claim,()->claims.confirmHandover(w.claim,w.applicant),()->claims.confirmHandover(w.claim,w.publisher)));
        assertEquals("COMPLETED",claims.detail(w.claim,w.applicant).status());
        assertEquals("COMPLETED",posts.getById(w.post).getStatus());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM handover_confirmations WHERE claim_id=?",Integer.class,w.claim));
    }
    @Test void finalConfirmationAndDisputeCannotBothCommit() throws Exception {
        var w=waiting(); claims.confirmHandover(w.claim,w.applicant);
        var results=race("posts",w.post,()->claims.confirmHandover(w.claim,w.publisher),
                ()->disputes.raise(w.claim,w.applicant,new RaiseDisputeRequest("race","private",List.of())));
        assertEquals(1,results.stream().filter(x->x==200).count());
        String status=claims.detail(w.claim,w.applicant).status();
        int open=jdbc.queryForObject("SELECT COUNT(*) FROM disputes WHERE claim_id=? AND status='OPEN'",Integer.class,w.claim);
        assertTrue((status.equals("COMPLETED") && open==0) || (status.equals("WAITING_HANDOVER") && open==1));
        assertEquals(status.equals("COMPLETED")?"COMPLETED":"HANDOVER",posts.getById(w.post).getStatus());
    }
    @Test void oneClaimCannotResolveTwoLostPostsConcurrently() throws Exception {
        var w=waiting(); claims.confirmHandover(w.claim,w.applicant); claims.confirmHandover(w.claim,w.publisher);
        long l1=createPost(w.applicant,"LOST"),l2=createPost(w.applicant,"LOST");
        var results=race("claims",w.claim,()->claims.resolveLost(w.claim,w.applicant,l1),()->claims.resolveLost(w.claim,w.applicant,l2));
        assertEquals(1,results.stream().filter(x->x==200).count());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE resolved_by_claim_id=?",Integer.class,w.claim));
    }
    @Test void withdrawingPostCannotLeaveNewPendingClaim() throws Exception {
        long a=user(),b=user(),p=createPost(b,"FOUND");
        var results=race("posts",p,()->posts.withdraw(p,b),()->claims.submit(p,a,new SubmitClaimRequest("proof",List.of())));
        assertEquals(1,results.stream().filter(x->x==200).count());
        int active=jdbc.queryForObject("SELECT COUNT(*) FROM claims WHERE post_id=? AND status='PENDING'",Integer.class,p);
        assertEquals(posts.getById(p).getStatus().equals("WITHDRAWN")?0:1,active);
    }
    @Test void closedLeadNeverReopensUnderConcurrentReview() throws Exception {
        long a=user(),b=user(),p=createPost(b,"LOST"),lead=leads.submit(p,a,new SubmitLeadRequest("clue",List.of())).id();
        race("lost_leads",lead,()->leads.review(lead,b,"CLOSED"),()->leads.review(lead,b,"VIEWED"));
        assertEquals("CLOSED",leads.get(lead,b).status());
    }
    @Test void loginAndImmediateRefreshIssueDifferentWorkingSessions() throws Exception {
        var om=new com.fasterxml.jackson.databind.ObjectMapper();
        String body="{\"testUser\":\"final-"+UUID.randomUUID()+"\"}";
        String first=om.readTree(mvc.perform(post("/auth/mock/login").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("accessToken").asText();
        String second=om.readTree(mvc.perform(post("/auth/mock/login").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("accessToken").asText();
        assertNotEquals(first,second);
        String fresh=om.readTree(mvc.perform(post("/auth/refresh").header("Authorization","Bearer "+second))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("accessToken").asText();
        assertNotEquals(second,fresh);
        mvc.perform(get("/users/me").header("Authorization","Bearer "+second)).andExpect(status().isUnauthorized());
        mvc.perform(get("/users/me").header("Authorization","Bearer "+fresh)).andExpect(status().isOk());
    }
    @Test void disabledAccountInvalidatesExistingSession() throws Exception {
        long u=user(); String bearer=token(u,"USER"); users.updateStatus(u,"DISABLED");
        mvc.perform(get("/users/me").header("Authorization",bearer)).andExpect(status().isUnauthorized());
    }
    @Test void unassignedAdminCannotReadPrivateDetailsOrResolve() throws Exception {
        var w=waiting(); long admin=user();
        long d=disputes.raise(w.claim,w.applicant,new RaiseDisputeRequest("reason","private-marker",List.of())).id();
        String auth=token(admin,"ADMIN");
        mvc.perform(get("/admin/disputes/"+d).header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.description").isEmpty());
        mvc.perform(post("/admin/disputes/"+d+"/resolution").header("Authorization",auth).contentType("application/json")
                .content("{\"resolutionType\":\"CONTINUE\",\"resolutionNote\":\"note\"}")).andExpect(status().isNotFound());
    }
    @Test void assignedAdminCanReadOriginalClaimProofButAnotherCannot() throws Exception {
        long a=user(),b=user(),admin=user(),other=user(),p=createPost(b,"FOUND");
        var file=files.upload(a,png(),"PRIVATE_CLAIM");
        long c=claims.submit(p,a,new SubmitClaimRequest("private",List.of(file.getId()))).id(); claims.review(c,b,new ReviewRequest("ACCEPT",null));
        long d=disputes.raise(c,a,new RaiseDisputeRequest("r",null,List.of())).id(); disputes.assign(d,admin);
        String auth=token(admin,"ADMIN");
        mvc.perform(get("/claims/"+c).header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.createdAt").exists());
        mvc.perform(get("/files/"+file.getId()).header("Authorization",auth)).andExpect(status().isOk());
        mvc.perform(get("/files/"+file.getId()).header("Authorization",token(other,"ADMIN"))).andExpect(status().isNotFound());
    }
    MockMultipartFile png() { return new MockMultipartFile("file","a.png","image/png",new byte[]{(byte)137,80,78,71,13,10,26,10,0,0,0,0}); }
    @Test void removedPostImageNotAnonymousAndGovernanceHistoryAvailable() throws Exception {
        long owner=user(),admin=user(); var file=files.upload(owner,png(),"PUBLIC_POST");
        long p=posts.create(owner,new CreatePostRequest("FOUND","wallet","wallet","public","S","Lib",null,List.of(file.getId()))).id();
        mvc.perform(get("/files/"+file.getId())).andExpect(status().isOk());
        String auth=token(admin,"ADMIN");
        mvc.perform(post("/admin/posts/"+p+"/remove").header("Authorization",auth).contentType("application/json").content("{\"reason\":\"reason\"}")).andExpect(status().isOk());
        mvc.perform(get("/files/"+file.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/admin/posts/"+p).header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.history[0].reason").value("reason"));
        mvc.perform(get("/admin/posts/"+p).header("Authorization",token(owner,"USER"))).andExpect(status().isForbidden());
    }
    @Test void badParametersAndBlankPartialUpdateAre400() throws Exception {
        for(String path:List.of("/posts?page=abc","/posts/abc","/posts/search?eventFrom=bad")) mvc.perform(get(path)).andExpect(status().isBadRequest());
        long u=user(),p=createPost(u,"LOST"); String auth=token(u,"USER");
        mvc.perform(post("/files").header("Authorization",auth)).andExpect(status().isBadRequest());
        mvc.perform(patch("/posts/"+p).header("Authorization",auth).contentType("application/json").content("{\"title\":\"  \"}")).andExpect(status().isBadRequest());
    }
    @Test void outsiderResourceLookupsUse404() throws Exception {
        var w=waiting(); long lost=createPost(w.publisher,"LOST"),lead=leads.submit(lost,w.applicant,new SubmitLeadRequest("clue",List.of())).id();
        String auth=token(user(),"USER");
        mvc.perform(get("/posts/"+w.post+"/claims").header("Authorization",auth)).andExpect(status().isNotFound());
        mvc.perform(get("/posts/"+lost+"/leads").header("Authorization",auth)).andExpect(status().isNotFound());
        mvc.perform(post("/leads/"+lead+"/review").header("Authorization",auth).contentType("application/json").content("{\"status\":\"VIEWED\"}")).andExpect(status().isNotFound());
    }
    @Test void auditReasonsSupportControlCharacters() {
        var w=waiting(); String reason="first\nsecond\t\"quoted\""; claims.cancelHandover(w.claim,w.applicant,reason);
        assertEquals(reason,jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.reason')) FROM audit_logs WHERE action='HANDOVER_CANCELLED' AND target_id=? ORDER BY id DESC LIMIT 1",String.class,w.claim));
    }
    @Test void timestampsNormalizeAndDatabaseSessionIsUtc() throws Exception {
        long u=user(); String auth=token(u,"USER");
        mvc.perform(post("/posts").header("Authorization",auth).contentType("application/json").content("{\"type\":\"LOST\",\"title\":\"t\",\"category\":\"wallet\",\"publicDescription\":\"d\",\"eventTime\":\"2026-09-27T12:00:00+08:00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.eventTime").value("2026-09-27T04:00:00Z"));
        assertEquals("+00:00",jdbc.queryForObject("SELECT @@session.time_zone",String.class));
    }
    @Test void fixedDatasetPaginationHasNoOverlap() {
        for(int i=0;i<45;i++) user();
        var mapper=users;
        var first=mapper.search("final-",0,20).stream().map(User::getId).toList();
        var second=mapper.search("final-",20,20).stream().map(User::getId).toList();
        assertEquals(20,first.size()); assertEquals(20,second.size()); assertTrue(Collections.disjoint(first,second));
    }
    @Test void physicalDeleteFailurePreservesOrphanForRetry() throws Exception {
        long u=user(); var f=files.upload(u,png(),"PUBLIC_POST");
        Path path=Path.of(props.getFile().getStorageRoot()).resolve(f.getStorageKey());
        Files.delete(path); Files.createDirectory(path); Files.writeString(path.resolve("locked-child"),"retry");
        jdbc.update("UPDATE files SET created_at=UTC_TIMESTAMP()-INTERVAL 2 DAY WHERE id=?",f.getId());
        try { files.cleanupOrphanFiles(); assertNotNull(fileMapper.findById(f.getId())); }
        finally { Files.delete(path.resolve("locked-child")); Files.delete(path); }
        files.cleanupOrphanFiles(); assertNull(fileMapper.findById(f.getId()));
    }

    @Test void cleanupRechecksBindingAfterWaitingForFileLock() throws Exception {
        long u=user(),postId=createPost(u,"FOUND");
        var f=files.upload(u,png(),"PUBLIC_POST");
        Path path=Path.of(props.getFile().getStorageRoot()).resolve(f.getStorageKey());
        jdbc.update("UPDATE files SET created_at=UTC_TIMESTAMP()-INTERVAL 2 DAY WHERE id=?",f.getId());
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try (var connection=dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("SELECT id FROM files WHERE id=? FOR UPDATE")) {
                statement.setLong(1,f.getId()); statement.executeQuery().close();
            }
            CountDownLatch entered=new CountDownLatch(1);
            var cleaning=executor.submit(()->{entered.countDown(); return files.cleanupOrphanFiles();});
            assertTrue(entered.await(5,TimeUnit.SECONDS)); Thread.sleep(400);
            assertFalse(cleaning.isDone(),"清理应等待正在绑定的文件行锁");
            try(var bound=connection.prepareStatement("UPDATE files SET bound=1 WHERE id=?");
                var image=connection.prepareStatement("INSERT INTO post_images(post_id,file_id,sort_order) VALUES (?,?,0)")) {
                bound.setLong(1,f.getId()); bound.executeUpdate();
                image.setLong(1,postId); image.setLong(2,f.getId()); image.executeUpdate();
            }
            connection.commit();
            cleaning.get(10,TimeUnit.SECONDS);
            assertNotNull(fileMapper.findById(f.getId()));
            assertTrue(Files.exists(path));
            assertTrue(fileMapper.publiclyVisible(f.getId()));
        } finally { executor.shutdownNow(); }
    }
}
