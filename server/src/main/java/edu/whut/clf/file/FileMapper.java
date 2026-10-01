package edu.whut.clf.file;

import edu.whut.clf.file.model.StoredFile;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FileMapper {

    @Insert("""
            INSERT INTO files (owner_id, purpose, storage_key, mime_type, size, visibility, bound)
            VALUES (#{ownerId}, #{purpose}, #{storageKey}, #{mimeType}, #{size}, #{visibility}, #{bound})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(StoredFile file);

    @Select("SELECT * FROM files WHERE id = #{id}")
    StoredFile findById(Long id);

    @Update("UPDATE files SET bound = 1 WHERE id = #{id}")
    int markBound(Long id);

    @Update("UPDATE files SET bound = 0 WHERE id = #{id}")
    int markUnbound(Long id);

    /** D13/R6：未绑定且早于 cutoff 的孤儿文件（上传后 24h 未绑定任何业务对象）。 */
    @Select("SELECT * FROM files WHERE bound = 0 AND created_at < #{cutoff}")
    List<StoredFile> findOrphans(@Param("cutoff") LocalDateTime cutoff);

    @Delete("DELETE FROM files WHERE id = #{id}")
    int deleteById(Long id);
}
