package edu.whut.clf.file;

import edu.whut.clf.common.security.AuthContext;
import edu.whut.clf.common.web.ApiResponse;
import edu.whut.clf.file.model.StoredFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** M3 文件上传/鉴权下载（FR-FILE-01）。 */
@RestController
@RequestMapping("/files")
@Tag(name = "M3-File", description = "受控文件")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping
    @Operation(summary = "上传文件 FR-FILE-01")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                   @RequestParam("purpose") String purpose) {
        StoredFile stored = fileService.upload(AuthContext.currentUserId(), file, purpose);
        return ApiResponse.ok(Map.of("fileId", stored.getId(), "mimeType", stored.getMimeType()));
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "鉴权下载（私密无权返回 404）")
    public ResponseEntity<Resource> download(@PathVariable Long fileId) {
        FileService.LoadedFile lf = fileService.download(fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(lf.mimeType()))
                .body(new ByteArrayResource(lf.bytes()));
    }
}
