package com.zdmj.userAuthService.mapper;

import com.zdmj.userAuthService.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper {

    /**
     * 插入用户
     * 
     * @param user 用户实体
     * @return 影响行数
     */
    int insert(User user);

    /**
     * 根据ID查询用户
     * 
     * @param id 用户ID
     * @return 用户实体
     */
    User selectById(@Param("id") Long id);

    /**
     * 根据用户名查询用户
     * 
     * @param username 用户名
     * @return 用户实体
     */
    User selectByUsername(@Param("username") String username);

    /**
     * 根据邮箱查询用户
     * 
     * @param email 邮箱
     * @return 用户实体
     */
    User selectByEmail(@Param("email") String email);

    /**
     * 检查用户名是否存在
     * 
     * @param username 用户名
     * @return 数量
     */
    int countByUsername(@Param("username") String username);

    /**
     * 检查邮箱是否存在
     * 
     * @param email 邮箱
     * @return 数量
     */
    int countByEmail(@Param("email") String email);

    /**
     * 更新用户密码
     * 
     * @param id          用户ID
     * @param newPassword 新密码（已加密）
     * @return 影响行数
     */
    int updatePassword(@Param("id") Long id, @Param("newPassword") String newPassword);
}
