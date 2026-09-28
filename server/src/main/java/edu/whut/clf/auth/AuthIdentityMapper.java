package edu.whut.clf.auth;

import edu.whut.clf.auth.model.AuthIdentity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AuthIdentityMapper {

    @Insert("""
            INSERT INTO auth_identities (user_id, provider, provider_subject)
            VALUES (#{userId}, #{provider}, #{providerSubject})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuthIdentity identity);

    @Select("SELECT * FROM auth_identities WHERE provider = #{provider} AND provider_subject = #{subject}")
    AuthIdentity findByProviderSubject(@Param("provider") String provider, @Param("subject") String subject);
}
