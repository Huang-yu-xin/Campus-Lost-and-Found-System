package edu.whut.clf.user;

import edu.whut.clf.user.model.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper {

    @Insert("""
            INSERT INTO users (nickname, campus, avatar_file_id, status, campus_verification_status)
            VALUES (#{nickname}, #{campus}, #{avatarFileId}, #{status}, #{campusVerificationStatus})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Select("SELECT * FROM users WHERE id = #{id}")
    User findById(Long id);

    @Update("""
            UPDATE users SET nickname = #{nickname}, campus = #{campus}, avatar_file_id = #{avatarFileId}
            WHERE id = #{id}
            """)
    int updateProfile(User user);

    @Update("UPDATE users SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Select("""
            <script>
            SELECT * FROM users
            <where>
              <if test="keyword != null and keyword != ''"> nickname LIKE CONCAT('%', #{keyword}, '%') </if>
            </where>
            ORDER BY created_at DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<User> search(@Param("keyword") String keyword, @Param("offset") long offset, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM users
            <where>
              <if test="keyword != null and keyword != ''"> nickname LIKE CONCAT('%', #{keyword}, '%') </if>
            </where>
            </script>
            """)
    long countSearch(@Param("keyword") String keyword);
}
