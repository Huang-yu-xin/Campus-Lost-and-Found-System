package edu.whut.clf.lead;

import edu.whut.clf.common.enums.FilePurpose;
import edu.whut.clf.common.enums.LeadStatus;
import edu.whut.clf.common.enums.PostStatus;
import edu.whut.clf.common.enums.PostType;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import edu.whut.clf.common.web.PageResult;
import edu.whut.clf.file.FileService;
import edu.whut.clf.lead.dto.LeadDtos.*;
import edu.whut.clf.lead.model.LostLead;
import edu.whut.clf.post.PostService;
import edu.whut.clf.post.model.Post;
import edu.whut.clf.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** M6 寻物线索（FR-LEAD-01/02）。仅 LOST；与 FOUND 认领严格隔离。 */
@Service
public class LeadService {

    private final LostLeadMapper leadMapper;
    private final LeadEvidenceFileMapper evidenceMapper;
    private final PostService postService;
    private final UserService userService;
    private final FileService fileService;

    public LeadService(LostLeadMapper leadMapper, LeadEvidenceFileMapper evidenceMapper,
                       PostService postService, UserService userService, FileService fileService) {
        this.leadMapper = leadMapper;
        this.evidenceMapper = evidenceMapper;
        this.postService = postService;
        this.userService = userService;
        this.fileService = fileService;
    }

    @Transactional
    public LeadItem submit(Long postId, Long userId, SubmitLeadRequest req) {
        userService.requireNotRestricted(userId);
        Post post = postService.getById(postId);
        if (!PostType.LOST.name().equals(post.getType())) {
            throw BusinessException.of(ErrorCode.POST_NOT_LOST);
        }
        if (!PostStatus.ACTIVE.name().equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该寻物信息当前不可提交线索");
        }
        if (Objects.equals(post.getPublisherId(), userId)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "不能给自己的寻物信息提交线索");
        }
        List<Long> files = req.evidenceFileIds();
        if (files != null) {
            for (Long fid : files) {
                fileService.requireOwnedFile(fid, userId, FilePurpose.PRIVATE_LEAD);
            }
        }
        LostLead lead = new LostLead();
        lead.setLostPostId(postId);
        lead.setReporterId(userId);
        lead.setBody(req.body().trim());
        lead.setStatus(LeadStatus.SUBMITTED.name());
        leadMapper.insert(lead);
        if (files != null) {
            for (Long fid : files) {
                evidenceMapper.insert(lead.getId(), fid);
                fileService.markBound(fid);
            }
        }
        return toItem(lead, true);
    }

    /** 查看单条线索（线索提交者或寻物发布者可见，否则 404）。 */
    public LeadItem get(Long leadId, Long userId) {
        LostLead lead = leadMapper.findById(leadId);
        if (lead == null) {
            throw BusinessException.of(ErrorCode.LEAD_NOT_FOUND);
        }
        Post post = postService.getById(lead.getLostPostId());
        boolean isReporter = Objects.equals(lead.getReporterId(), userId);
        boolean isPublisher = Objects.equals(post.getPublisherId(), userId);
        if (!isReporter && !isPublisher) {
            throw BusinessException.of(ErrorCode.LEAD_NOT_FOUND);
        }
        return toItem(lead, true);
    }

    /** 寻物发布者查看收到的线索。 */
    public List<LeadItem> postLeads(Long postId, Long userId) {
        Post post = postService.getById(postId);
        if (!Objects.equals(post.getPublisherId(), userId)) {
            throw BusinessException.of(ErrorCode.FORBIDDEN);
        }
        return leadMapper.findByPost(postId).stream().map(l -> toItem(l, true)).toList();
    }

    public PageResult<LeadItem> myLeads(Long userId, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = pageSize <= 0 || pageSize > 100 ? 20 : pageSize;
        List<LostLead> list = leadMapper.findByReporter(userId, (p - 1) * size, size);
        long total = leadMapper.countByReporter(userId);
        return PageResult.of(list.stream().map(l -> toItem(l, true)).toList(), total, p, size);
    }

    /** 我作为寻物发布者收到的所有线索（B10 / FR-LEAD-02）。 */
    public PageResult<edu.whut.clf.lead.dto.ReceivedLeadItem> receivedLeads(Long userId, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = pageSize <= 0 || pageSize > 100 ? 20 : pageSize;
        var items = leadMapper.findReceivedByPublisher(userId, (p - 1) * size, size);
        long total = leadMapper.countReceivedByPublisher(userId);
        return PageResult.of(items, total, p, size);
    }

    @Transactional
    public void review(Long leadId, Long userId, String statusRaw) {
        LostLead lead = leadMapper.findById(leadId);
        if (lead == null) {
            throw BusinessException.of(ErrorCode.LEAD_NOT_FOUND);
        }
        Post post = postService.getById(lead.getLostPostId());
        if (!Objects.equals(post.getPublisherId(), userId)) {
            throw BusinessException.of(ErrorCode.FORBIDDEN);
        }
        LeadStatus status;
        try {
            status = LeadStatus.valueOf(statusRaw);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "非法线索状态");
        }
        // 线索处理不擅自判定物品归属，仅记录处理进度
        if (status == LeadStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "不能回退为未处理");
        }
        leadMapper.updateStatus(leadId, status.name());
    }

    private LeadItem toItem(LostLead l, boolean includeEvidence) {
        List<Long> ev = includeEvidence ? evidenceMapper.findFileIds(l.getId()) : List.of();
        return new LeadItem(l.getId(), l.getLostPostId(), l.getReporterId(), l.getBody(),
                l.getStatus(), l.getCreatedAt(), ev);
    }
}
