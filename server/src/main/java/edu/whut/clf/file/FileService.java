package edu.whut.clf.file;

import edu.whut.clf.common.config.AppProperties;
import edu.whut.clf.common.enums.FilePurpose;
import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import edu.whut.clf.common.security.AuthContext;
import edu.whut.clf.common.security.Principal;
import edu.whut.clf.file.model.StoredFile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 受控文件存储：真实文件类型（magic bytes）校验、随机文件名、用途/归属、鉴权下载。
 * FR-FILE-01 / NFR-SEC-02。
 */
@Service
public class FileService {

    private final AppProperties props;
    private final FileMapper fileMapper;
    private final List<FilePrivateAccessChecker> checkers;
    private final Set<String> allowedMime;

    public FileService(AppProperties props, FileMapper fileMapper, List<FilePrivateAccessChecker> checkers) {
        this.props = props;
        this.fileMapper = fileMapper;
        this.checkers = checkers;
        this.allowedMime = Set.of(props.getFile().getAllowedMime().split("\\s*,\\s*"));
    }

    public StoredFile upload(Long ownerId, MultipartFile multipart, String purposeRaw) {
        FilePurpose purpose;
        try {
            purpose = FilePurpose.valueOf(purposeRaw);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "非法文件用途");
        }
        if (multipart == null || multipart.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "文件为空");
        }
        long maxBytes = (long) props.getFile().getMaxSizeMb() * 1024 * 1024;
        if (multipart.getSize() > maxBytes) {
            throw BusinessException.of(ErrorCode.FILE_TOO_LARGE);
        }
        byte[] head;
        byte[] content;
        try {
            content = multipart.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "读取文件失败");
        }
        head = content.length >= 12 ? java.util.Arrays.copyOf(content, 12) : content;
        String realMime = sniffImageMime(head);
        if (realMime == null || !allowedMime.contains(realMime)) {
            // 真实文件头与允许类型不符（防伪装图片）
            throw BusinessException.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        String ext = switch (realMime) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
        String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String storageKey = yearMonth + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
        try {
            Path target = Paths.get(props.getFile().getStorageRoot()).resolve(storageKey);
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件写入失败");
        }

        StoredFile f = new StoredFile();
        f.setOwnerId(ownerId);
        f.setPurpose(purpose.name());
        f.setStorageKey(storageKey);
        f.setMimeType(realMime);
        f.setSize(multipart.getSize());
        f.setVisibility(purpose.isPublicVisible() ? "PUBLIC" : "PRIVATE");
        f.setBound(false);
        fileMapper.insert(f);
        return f;
    }

    /** 读取文件字节（含鉴权）。无权时按 404 处理避免枚举。 */
    public LoadedFile download(Long fileId) {
        StoredFile file = fileMapper.findById(fileId);
        if (file == null) {
            throw BusinessException.of(ErrorCode.NOT_FOUND);
        }
        if (!canAccess(file)) {
            throw BusinessException.of(ErrorCode.NOT_FOUND);
        }
        try {
            Path path = Paths.get(props.getFile().getStorageRoot()).resolve(file.getStorageKey());
            byte[] bytes = Files.readAllBytes(path);
            return new LoadedFile(bytes, file.getMimeType());
        } catch (IOException e) {
            throw BusinessException.of(ErrorCode.NOT_FOUND);
        }
    }

    private boolean canAccess(StoredFile file) {
        if ("PUBLIC".equals(file.getVisibility())) {
            return true;
        }
        Principal p = AuthContext.current();
        if (p == null) {
            return false;
        }
        if (p.isAdmin()) {
            // 管理员对私密证据的读取仍受各模块 checker 约束（如仅获授权争议）
            for (FilePrivateAccessChecker c : checkers) {
                if (c.supports(file.getPurpose()) && c.canAccess(p.userId(), file)) {
                    return true;
                }
            }
            return false;
        }
        if (file.getOwnerId().equals(p.userId())) {
            return true;
        }
        for (FilePrivateAccessChecker c : checkers) {
            if (c.supports(file.getPurpose()) && c.canAccess(p.userId(), file)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验文件属于指定用户且用途匹配（绑定业务对象前调用）。
     * A3(P1-B2)：已 bound=1 的文件拒绝二次绑定（默认对所有用途一律拒绝，保持简单），
     * 防止同一上传被绑定到多个业务对象。替换语义的更新流程须先 {@link #markUnbound} 释放旧文件。
     */
    public StoredFile requireOwnedFile(Long fileId, Long ownerId, FilePurpose expectedPurpose) {
        StoredFile f = fileMapper.findById(fileId);
        if (f == null || !f.getOwnerId().equals(ownerId) || !f.getPurpose().equals(expectedPurpose.name())
                || Boolean.TRUE.equals(f.getBound())) {
            throw BusinessException.of(ErrorCode.INVALID_EVIDENCE_FILE);
        }
        return f;
    }

    /**
     * 替换式绑定的入参规整（post/claim/lead/dispute 四处同构逻辑共用）：
     * LinkedHashSet 去重保序 → 数量上限（统一用 maxCountPerPost）→ 逐个归属/用途校验（含拒绝二次绑定）。
     * 返回去重后的有序 fileId 列表；入参 null 返回空列表。超限抛 INVALID_ARGUMENT(400)。
     */
    public List<Long> prepareReplaceBinding(List<Long> fileIds, Long ownerId, FilePurpose purpose) {
        if (fileIds == null) {
            return List.of();
        }
        List<Long> unique = new ArrayList<>(new LinkedHashSet<>(fileIds));
        int max = props.getFile().getMaxCountPerPost();
        if (unique.size() > max) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "文件数量超过上限 " + max);
        }
        for (Long fid : unique) {
            requireOwnedFile(fid, ownerId, purpose);
        }
        return unique;
    }

    public void markBound(Long fileId) {
        fileMapper.markBound(fileId);
    }

    /** 释放绑定（替换语义更新时先释放旧文件，使其可被重新绑定或被孤儿清理回收）。 */
    public void markUnbound(Long fileId) {
        fileMapper.markUnbound(fileId);
    }

    /** 依 magic bytes 识别图片真实类型；非图片返回 null。 */
    static String sniffImageMime(byte[] b) {
        if (b == null) {
            return null;
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    public record LoadedFile(byte[] bytes, String mimeType) {}
}
