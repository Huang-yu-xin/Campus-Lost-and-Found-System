package edu.whut.clf.message;

import edu.whut.clf.common.security.AuthContext;
import edu.whut.clf.common.web.ApiResponse;
import edu.whut.clf.message.dto.MessageDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** M6 申请内留言（FR-MSG-01）。 */
@RestController
@RequestMapping("/claims/{claimId}/messages")
@Tag(name = "M6-Message", description = "申请内留言")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    @Operation(summary = "读取留言（自动标记已读）FR-MSG-01")
    public ApiResponse<List<MessageItem>> list(@PathVariable Long claimId) {
        return ApiResponse.ok(messageService.list(claimId, AuthContext.currentUserId()));
    }

    @PostMapping
    @Operation(summary = "发送留言 FR-MSG-01")
    public ApiResponse<MessageItem> send(@PathVariable Long claimId, @Valid @RequestBody SendMessageRequest req) {
        return ApiResponse.ok(messageService.send(claimId, AuthContext.currentUserId(), req.body()));
    }
}
