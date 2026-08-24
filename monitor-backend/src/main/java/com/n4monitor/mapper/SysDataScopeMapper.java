package com.n4monitor.mapper;

import com.n4monitor.entity.SysDataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysDataScopeMapper {
    List<SysDataScope> findByGroupId(@Param("groupId") Long groupId);

    List<SysDataScope> findByUserId(@Param("userId") Long userId);

    int insert(SysDataScope scope);

    int update(SysDataScope scope);

    int delete(@Param("scopeId") Long scopeId);
}
