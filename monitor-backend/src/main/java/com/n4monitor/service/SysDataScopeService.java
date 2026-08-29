package com.n4monitor.service;

import com.n4monitor.entity.SysDataScope;
import com.n4monitor.mapper.SysDataScopeMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysDataScopeService {
    private final SysDataScopeMapper dataScopeMapper;

    public SysDataScopeService(SysDataScopeMapper dataScopeMapper) {
        this.dataScopeMapper = dataScopeMapper;
    }

    public List<SysDataScope> listByGroupId(Long groupId) {
        return dataScopeMapper.findByGroupId(groupId);
    }

    public List<SysDataScope> listByUserId(Long userId) {
        return dataScopeMapper.findByUserId(userId);
    }

    public void create(SysDataScope scope) {
        dataScopeMapper.insert(scope);
    }

    public void update(Long scopeId, SysDataScope scope) {
        scope.setScopeId(scopeId);
        dataScopeMapper.update(scope);
    }

    public void delete(Long scopeId) {
        dataScopeMapper.delete(scopeId);
    }
}
