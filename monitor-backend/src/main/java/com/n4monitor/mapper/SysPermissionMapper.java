package com.n4monitor.mapper;

import com.n4monitor.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysPermissionMapper {
    SysPermission findById(@Param("permId") Long permId);

    List<SysPermission> findAll(@Param("status") Integer status);

    List<SysPermission> findByUserId(@Param("userId") Long userId);

    List<String> findCodesByUserId(@Param("userId") Long userId);

    int insert(SysPermission permission);

    int update(SysPermission permission);

    int logicalDelete(@Param("permId") Long permId);

    int countGroupRefs(@Param("permId") Long permId);
}
