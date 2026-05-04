package com.example.ShoppingSystem.mapper;

import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户登录身份 Mapper。
 * 负责邮箱、GitHub、Google 等登录身份的查询、绑定与写入。
 */
@Mapper
public interface UserLoginIdentityMapper {

    UserLoginIdentity findByGithubId(@Param("githubId") String githubId);

    UserLoginIdentity findByEmail(@Param("email") String email);

    UserLoginIdentity findByGoogleId(@Param("googleId") String googleId);

    UserLoginIdentity findByMicrosoftId(@Param("microsoftId") String microsoftId);

    UserLoginIdentity findById(@Param("id") Long id);

    UserLoginIdentity findByUserId(@Param("userId") Long userId);

    UserLoginIdentity findVerifiedByPhone(@Param("phone") String phone);

    long countVerifiedPhones();

    java.util.List<String> listVerifiedPhones(@Param("limit") int limit, @Param("offset") long offset);

    int bindGithubIdById(@Param("id") Long id, @Param("githubId") String githubId);

    int bindGoogleIdById(@Param("id") Long id, @Param("googleId") String googleId);

    int bindMicrosoftIdById(@Param("id") Long id, @Param("microsoftId") String microsoftId);

    int updateLastLoginAtById(@Param("id") Long id);

    int updateLastLoginAtByUserId(@Param("userId") Long userId);

    int bindVerifiedPhoneByUserId(@Param("userId") Long userId, @Param("phone") String phone);

    int updateEmailPasswordHashByUserId(@Param("userId") Long userId,
                                         @Param("emailPasswordHash") String emailPasswordHash,
                                         @Param("tokenVersion") String tokenVersion);

    int savePendingTotpSecret(@Param("id") Long id, @Param("secretEncrypted") String secretEncrypted);

    int enableTotpById(@Param("id") Long id);

    int updateTotpLastUsedStep(@Param("id") Long id, @Param("timeStep") Long timeStep);

    int disableTotpById(@Param("id") Long id);

    int insertGithubIdentity(UserLoginIdentity entity);

    int insertGoogleIdentity(UserLoginIdentity entity);

    int insertMicrosoftIdentity(UserLoginIdentity entity);

    int insertEmailIdentity(UserLoginIdentity entity);
}
