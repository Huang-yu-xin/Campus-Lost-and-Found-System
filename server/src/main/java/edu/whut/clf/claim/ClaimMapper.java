package edu.whut.clf.claim;

import edu.whut.clf.claim.dto.ReceivedClaimItem;
import edu.whut.clf.claim.model.Claim;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ClaimMapper {

    @Insert("""
            INSERT INTO claims (post_id, applicant_id, description, status, version)
            VALUES (#{postId}, #{applicantId}, #{description}, #{status}, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Claim claim);

    @Select("SELECT * FROM claims WHERE id = #{id}")
    Claim findById(Long id);

    @Select("SELECT COUNT(*) FROM claims WHERE post_id = #{postId} AND status IN ('PENDING','WAITING_HANDOVER')")
    long countActiveByPost(Long postId);

    @Select("""
            SELECT COUNT(*) FROM claims
            WHERE post_id = #{postId} AND applicant_id = #{applicantId} AND status IN ('PENDING','WAITING_HANDOVER')
            """)
    long countActiveByPostAndApplicant(@Param("postId") Long postId, @Param("applicantId") Long applicantId);

    @Select("SELECT * FROM claims WHERE post_id = #{postId} ORDER BY created_at DESC")
    List<Claim> findByPost(Long postId);

    @Select("SELECT * FROM claims WHERE applicant_id = #{userId} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<Claim> findByApplicant(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM claims WHERE applicant_id = #{userId}")
    long countByApplicant(Long userId);

    /** 条件状态更新（乐观并发）。返回受影响行数。 */
    @Update("""
            UPDATE claims SET status = #{to}, version = version + 1
            WHERE id = #{id} AND status = #{from}
            """)
    int changeStatus(@Param("id") Long id, @Param("from") String from, @Param("to") String to);

    @Update("""
            UPDATE claims SET status = 'WAITING_HANDOVER', reviewed_by = #{reviewerId},
                   reviewed_at = #{now}, accepted_at = #{now}, version = version + 1
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int accept(@Param("id") Long id, @Param("reviewerId") Long reviewerId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE claims SET status = 'REJECTED', reviewed_by = #{reviewerId}, review_reason = #{reason},
                   reviewed_at = #{now}, version = version + 1
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int reject(@Param("id") Long id, @Param("reviewerId") Long reviewerId,
               @Param("reason") String reason, @Param("now") LocalDateTime now);

    /** 接受某申请时，同事务关闭同一发布的其他 PENDING 申请。 */
    @Update("""
            UPDATE claims SET status = 'CLOSED', review_reason = 'OTHER_CLAIM_ACCEPTED', version = version + 1
            WHERE post_id = #{postId} AND status = 'PENDING' AND id <> #{acceptedId}
            """)
    int closeOtherPending(@Param("postId") Long postId, @Param("acceptedId") Long acceptedId);

    @Update("UPDATE claims SET status = 'COMPLETED', completed_at = #{now}, version = version + 1 WHERE id = #{id} AND status = 'WAITING_HANDOVER'")
    int complete(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 发布者收到的所有申请（联表 posts）。命中 idx_post_publisher + claims.post_id 外键索引。 */
    @Select("""
            SELECT c.id, c.post_id, p.title AS post_title, p.type AS post_type,
                   c.applicant_id, c.status, c.created_at, c.reviewed_at
            FROM claims c JOIN posts p ON c.post_id = p.id
            WHERE p.publisher_id = #{publisherId}
            ORDER BY c.created_at DESC
            LIMIT #{offset}, #{limit}
            """)
    List<ReceivedClaimItem> findReceivedByPublisher(@Param("publisherId") Long publisherId,
                                                    @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM claims c JOIN posts p ON c.post_id = p.id WHERE p.publisher_id = #{publisherId}")
    long countReceivedByPublisher(Long publisherId);

    /** 治理下架发布时，关闭其有效申请（保留历史，标注原因）。 */
    @Update("""
            UPDATE claims SET status = 'CLOSED', review_reason = #{reason}, version = version + 1
            WHERE post_id = #{postId} AND status IN ('PENDING','WAITING_HANDOVER')
            """)
    int closeActiveByPost(@Param("postId") Long postId, @Param("reason") String reason);
}
