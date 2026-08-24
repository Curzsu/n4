package com.n4monitor.mapper;

import com.n4monitor.dto.GroupDto;
import com.n4monitor.entity.SysGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysGroupMapper {
    SysGroup findById(@Param("groupId") Long groupId);

    List<GroupDto> findAll(@Param("status") Integer status);

    List<GroupDto> findByUserId(@Param("userId") Long userId);

    int insert(SysGroup group);

    int update(SysGroup group);

    int logicalDelete(@Param("groupId") Long groupId);

    int countUsers(@Param("groupId") Long groupId);

    int deleteGroupPermissions(@Param("groupId") Long groupId);

    int insertGroupPermission(@Param("groupId") Long groupId, @Param("permId") Long permId);

    List<Long> findPermissionIds(@Param("groupId") Long groupId);
}
