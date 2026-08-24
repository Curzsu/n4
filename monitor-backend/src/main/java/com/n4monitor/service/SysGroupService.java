package com.n4monitor.service;

import com.n4monitor.dto.GroupDto;
import com.n4monitor.dto.GroupFormDto;
import com.n4monitor.entity.SysGroup;
import com.n4monitor.exception.BusinessException;
import com.n4monitor.mapper.SysGroupMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SysGroupService {
    private final SysGroupMapper groupMapper;

    public SysGroupService(SysGroupMapper groupMapper) {
        this.groupMapper = groupMapper;
    }

    public List<GroupDto> list(Integer status) {
        return groupMapper.findAll(status);
    }

    public List<GroupDto> listByUserId(Long userId) {
        return groupMapper.findByUserId(userId);
    }

    public void create(GroupFormDto form) {
        groupMapper.insert(toEntity(null, form));
    }

    public void update(Long groupId, GroupFormDto form) {
        groupMapper.update(toEntity(groupId, form));
    }

    public void delete(Long groupId) {
        if (groupMapper.countUsers(groupId) > 0) {
            throw new BusinessException("User group contains users and cannot be deleted");
        }
        groupMapper.logicalDelete(groupId);
    }

    public List<Long> permissionIds(Long groupId) {
        return groupMapper.findPermissionIds(groupId);
    }

    @Transactional
    public void assignPermissions(Long groupId, List<Long> permIds) {
        groupMapper.deleteGroupPermissions(groupId);
        if (permIds != null) {
            for (Long permId : permIds) {
                groupMapper.insertGroupPermission(groupId, permId);
            }
        }
    }

    private SysGroup toEntity(Long groupId, GroupFormDto form) {
        SysGroup group = new SysGroup();
        group.setGroupId(groupId);
        group.setGroupName(form.getGroupName());
        group.setGroupCode(form.getGroupCode());
        group.setDescription(form.getDescription());
        group.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        return group;
    }
}
