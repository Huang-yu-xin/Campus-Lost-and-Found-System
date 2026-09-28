package edu.whut.clf.match;

import edu.whut.clf.common.web.ApiResponse;
import edu.whut.clf.match.dto.MatchDtos.MatchCandidate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** M4 匹配（FR-MATCH-01/02）。结果仅供参考，不证明归属。 */
@RestController
@RequestMapping("/posts/{postId}/matches")
@Tag(name = "M4-Search", description = "检索与匹配")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping
    @Operation(summary = "双向候选匹配 FR-MATCH-01/02")
    public ApiResponse<Map<String, Object>> matches(@PathVariable Long postId) {
        List<MatchCandidate> items = matchService.matchesFor(postId);
        return ApiResponse.ok(Map.of(
                "items", items,
                "notice", "匹配结果仅供参考，不证明物品归属"));
    }
}
