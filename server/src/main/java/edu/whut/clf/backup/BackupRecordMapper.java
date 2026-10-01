package edu.whut.clf.backup;

import edu.whut.clf.backup.model.BackupRecord;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BackupRecordMapper {

    @Insert("""
            INSERT INTO backup_records (initiated_by, started_at, status)
            VALUES (#{initiatedBy}, #{startedAt}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BackupRecord record);

    @Update("""
            UPDATE backup_records SET status = #{status}, finished_at = #{finishedAt},
                   manifest_path = #{manifestPath}, checksum = #{checksum}
            WHERE id = #{id}
            """)
    int finish(@Param("id") Long id, @Param("status") String status, @Param("finishedAt") LocalDateTime finishedAt,
               @Param("manifestPath") String manifestPath, @Param("checksum") String checksum);

    @Select("SELECT * FROM backup_records ORDER BY started_at DESC LIMIT #{offset}, #{limit}")
    List<BackupRecord> list(@Param("offset") long offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM backup_records")
    long count();

    /** D13/R7：把早于 cutoff 仍 RUNNING 的记录置 FAILED（启动时清理僵尸批次）。 */
    @Update("UPDATE backup_records SET status = 'FAILED', finished_at = #{now} "
            + "WHERE status = 'RUNNING' AND started_at < #{cutoff}")
    int failRunningBefore(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);
}
