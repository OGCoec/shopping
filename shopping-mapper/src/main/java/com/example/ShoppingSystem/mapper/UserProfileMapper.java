package com.example.ShoppingSystem.mapper;

import com.example.ShoppingSystem.entity.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户资料 Mapper。
 * 负责 user_profile 的存在性判断与首次插入。
 */
@Mapper
public interface UserProfileMapper {

    @Select("SELECT EXISTS (SELECT 1 FROM user_profile WHERE id = #{id})")
    boolean existsById(@Param("id") Long id);

    int insertUserProfile(UserProfile profile);
}
