package com.example.ShoppingSystem.mapper;

import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户登录身份 Mapper。
 * 负责邮箱、GitHub、Google 等登录身份的查询、绑定与写入。
 */
@Mapper
public interface UserLoginIdentityMapper {

    @Select("""
            SELECT id, user_id, email, email_password_hash, email_verified,
                   phone, phone_verified, github_id, google_id, microsoft_id,
                   token_version, status, last_login_at, created_at, updated_at
            FROM user_login_identity
            WHERE github_id = #{githubId}
            LIMIT 1
            """)
    UserLoginIdentity findByGithubId(@Param("githubId") String githubId);

    @Select("""
            SELECT id, user_id, email, email_password_hash, email_verified,
                   phone, phone_verified, github_id, google_id, microsoft_id,
                   token_version, status, last_login_at, created_at, updated_at
            FROM user_login_identity
            WHERE email = #{email}
            LIMIT 1
            """)
    UserLoginIdentity findByEmail(@Param("email") String email);

    @Select("""
            SELECT id, user_id, email, email_password_hash, email_verified,
                   phone, phone_verified, github_id, google_id, microsoft_id,
                   token_version, status, last_login_at, created_at, updated_at
            FROM user_login_identity
            WHERE google_id = #{googleId}
            LIMIT 1
            """)
    UserLoginIdentity findByGoogleId(@Param("googleId") String googleId);

    @Select("""
            SELECT id, user_id, email, email_password_hash, email_verified,
                   phone, phone_verified, github_id, google_id, microsoft_id,
                   token_version, status, last_login_at, created_at, updated_at
            FROM user_login_identity
            WHERE microsoft_id = #{microsoftId}
            LIMIT 1
            """)
    UserLoginIdentity findByMicrosoftId(@Param("microsoftId") String microsoftId);

    @Update("""
            UPDATE user_login_identity
            SET github_id = #{githubId},
                last_login_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int bindGithubIdById(@Param("id") Long id, @Param("githubId") String githubId);

    @Update("""
            UPDATE user_login_identity
            SET google_id = #{googleId},
                last_login_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int bindGoogleIdById(@Param("id") Long id, @Param("googleId") String googleId);

    @Update("""
            UPDATE user_login_identity
            SET microsoft_id = #{microsoftId},
                last_login_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int bindMicrosoftIdById(@Param("id") Long id, @Param("microsoftId") String microsoftId);

    @Update("""
            UPDATE user_login_identity
            SET last_login_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateLastLoginAtById(@Param("id") Long id);

    int insertGithubIdentity(UserLoginIdentity entity);

    int insertGoogleIdentity(UserLoginIdentity entity);

    int insertMicrosoftIdentity(UserLoginIdentity entity);
}
