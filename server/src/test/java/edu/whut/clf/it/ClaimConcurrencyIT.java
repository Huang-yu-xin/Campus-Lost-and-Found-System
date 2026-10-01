package edu.whut.clf.it;

import edu.whut.clf.claim.ClaimMapper;
import edu.whut.clf.claim.ClaimService;
import edu.whut.clf.claim.dto.ClaimDtos.ReviewRequest;
import edu.whut.clf.claim.dto.ClaimDtos.SubmitClaimRequest;
import edu.whut.clf.claim.model.Claim;
import edu.whut.clf.common.enums.ClaimStatus;
import edu.whut.clf.common.enums.PostStatus;
import edu.whut.clf.common.enums.UserStatus;
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
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TC-CLAIM-03 并发接受：同一 FOUND 招领两个申请被并发接受，只允许一个进入 WAITING_HANDOVER。
 * 需真实 MySQL（Flyway 建表 + 行锁 + 生成列唯一索引）。本地无 DB 时跳过；CI 设置 CLF_IT=true 运行。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CLF_IT", matches = "true")
class ClaimConcurrencyIT {

    @Autowired PostService postService;
    @Autowired ClaimService claimService;
    @Autowired PostMapper postMapper;
    @Autowired ClaimMapper claimMapper;
    @Autowired UserMapper userMapper;

    @Test
    void concurrentAccept_onlyOneWaitingHandover() throws Exception {
        Long publisher = newUser("发布者");
        Long applicantA = newUser("申请人A");
        Long applicantB = newUser("申请人B");

        PostDetail post = postService.create(publisher, new CreatePostRequest(
                "FOUND", "捡到黑色钱包", "钱包", "图书馆捡到一个钱包", "南湖", "图书馆", null, List.of()));
        Long postId = post.id();

        var cA = claimService.submit(postId, applicantA, new SubmitClaimRequest("我的钱包，有校园卡", List.of()));
        var cB = claimService.submit(postId, applicantB, new SubmitClaimRequest("钱包是我的，有现金", List.of()));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        Future<?> fA = pool.submit(() -> runAccept(cA.id(), publisher, start, success, conflict));
        Future<?> fB = pool.submit(() -> runAccept(cB.id(), publisher, start, success, conflict));
        start.countDown();
        fA.get(10, TimeUnit.SECONDS);
        fB.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        // 恰好一个成功进入交接
        assertEquals(1, success.get(), "只应有一个申请被成功接受");
        assertEquals(1, conflict.get(), "另一个应冲突失败");

        Post reloaded = postMapper.findById(postId);
        assertEquals(PostStatus.HANDOVER.name(), reloaded.getStatus(), "发布应进入 HANDOVER");

        List<Claim> claims = claimMapper.findByPost(postId);
        long waiting = claims.stream().filter(c -> ClaimStatus.WAITING_HANDOVER.name().equals(c.getStatus())).count();
        assertEquals(1, waiting, "至多一个 WAITING_HANDOVER");
        assertEquals(1, claims.stream().filter(c -> ClaimStatus.CLOSED.name().equals(c.getStatus())).count(), "其余有效申请应关闭");
    }

    private void runAccept(Long claimId, Long publisher, CountDownLatch start,
                           AtomicInteger success, AtomicInteger conflict) {
        try {
            start.await();
            claimService.review(claimId, publisher, new ReviewRequest("ACCEPT", null));
            success.incrementAndGet();
        } catch (edu.whut.clf.common.error.BusinessException e) {
            assertEquals(409, e.getErrorCode().httpStatus().value());
            conflict.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private Long newUser(String nickname) {
        User u = new User();
        u.setNickname(nickname);
        u.setStatus(UserStatus.ACTIVE.name());
        u.setCampusVerificationStatus("UNVERIFIED");
        userMapper.insert(u);
        return u.getId();
    }
}
