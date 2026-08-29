package com.n4monitor.mapper;

import com.n4monitor.dto.UserDto;
import com.n4monitor.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysUserMapper {
    SysUser findById(@Param("userId") Long userId);

    SysUser findByUsername(@Param("username") String username);

    long countByUsername(@Param("username") String username);

    List<UserDto> findPage(@Param("username") String username, @Param("realName") String realName, @Param("status") Integer status, @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countPage(@Param("username") String username, @Param("realName") String realName, @Param("status") Integer status);

    int insert(SysUser user);

    int update(SysUser user);

    int logicalDelete(@Param("userId") Long userId);

    int updateStatus(@Param("userId") Long userId, @Param("status") Integer status);

    int updatePassword(@Param("userId") Long userId, @Param("password") String password);

    int updateLastLoginTime(@Param("userId") Long userId);

    int deleteUserGroups(@Param("userId") Long userId);

    int insertUserGroup(@Param("userId") Long userId, @Param("groupId") Long groupId);
}
