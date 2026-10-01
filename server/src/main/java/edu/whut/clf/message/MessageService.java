package edu.whut.clf.message;

import edu.whut.clf.claim.ClaimAccessService;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import edu.whut.clf.message.dto.MessageDtos.MessageItem;
import edu.whut.clf.message.model.ClaimMessage;
import edu.whut.clf.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** M6 申请内私密留言（FR-MSG-01）。仅申请双方可访问。 */
@Service
public class MessageService {

    private static final int MAX_BODY = 1000;

    private final ClaimMessageMapper messageMapper;
    private final ClaimAccessService claimAccess;
    private final UserService userService;

    public MessageService(ClaimMessageMapper messageMapper, ClaimAccessService claimAccess, UserService userService) {
        this.messageMapper = messageMapper;
        this.claimAccess = claimAccess;
        this.userService = userService;
    }

    @Transactional
    public List<MessageItem> list(Long claimId, Long userId) {
        claimAccess.requireParticipant(claimId, userId);
        messageMapper.markRead(claimId, userId, LocalDateTime.now(java.time.Clock.systemUTC()));
        return messageMapper.findByClaim(claimId).stream()
                .map(m -> new MessageItem(m.getId(), m.getSenderId(),
                        m.getSenderId().equals(userId), m.getBody(), m.getCreatedAt(), m.getReadAt() != null))
                .toList();
    }

    @Transactional
    public MessageItem send(Long claimId, Long userId, String body) {
        claimAccess.requireParticipant(claimId, userId);
        userService.requireNotRestricted(userId);
        if (body == null || body.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "留言不能为空");
        }
        String trimmed = body.trim();
        if (trimmed.length() > MAX_BODY) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "留言过长");
        }
        ClaimMessage m = new ClaimMessage();
        m.setClaimId(claimId);
        m.setSenderId(userId);
        m.setBody(trimmed);
        messageMapper.insert(m);
        return new MessageItem(m.getId(), userId, true, trimmed, LocalDateTime.now(java.time.Clock.systemUTC()), false);
    }
}
