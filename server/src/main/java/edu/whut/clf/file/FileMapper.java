package edu.whut.clf.file;

import edu.whut.clf.file.model.StoredFile;
import org.apache.ibatis.annotations.*;

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
}
