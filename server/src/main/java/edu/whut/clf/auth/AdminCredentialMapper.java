package edu.whut.clf.auth;

import edu.whut.clf.auth.model.AdminCredential;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdminCredentialMapper {

    @Insert("""
            INSERT INTO admin_credentials (admin_user_id, username, password_hash, security_status)
            VALUES (#{adminUserId}, #{username}, #{passwordHash}, #{securityStatus})
            """)
    int insert(AdminCredential credential);

    @Select("SELECT * FROM admin_credentials WHERE username = #{username}")
    AdminCredential findByUsername(String username);
}
