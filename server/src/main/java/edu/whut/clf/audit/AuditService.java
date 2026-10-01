package edu.whut.clf.audit;

import edu.whut.clf.audit.model.AuditLog;
import edu.whut.clf.common.web.PageResult;
import edu.whut.clf.common.web.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/** 审计日志记录与查询（FR-AUDIT-01）。 */
@Service
public class AuditService {

    private final AuditLogMapper mapper;

    public AuditService(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    public void record(Long actorId, String actorType, String action, String targetType, Long targetId,
                       String result, String metadataJson) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setActorType(actorType);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setResult(result);
        log.setMetadata(metadataJson);
        mapper.insert(log);
    }

    public PageResult<AuditLog> search(String action, String targetType, int page, int pageSize) {
        return search(action, targetType, null, page, pageSize);
    }

    public PageResult<AuditLog> search(String action, String targetType, Long targetId, int page, int pageSize) {
        Pageable pg = Pageable.of(page, pageSize);
        List<AuditLog> items = mapper.search(action, targetType, targetId, pg.offset(), pg.size());
        long total = mapper.count(action, targetType, targetId);
        return PageResult.of(items, total, pg.page(), pg.size());
    }
}
