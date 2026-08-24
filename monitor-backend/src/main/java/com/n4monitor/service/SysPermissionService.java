package com.n4monitor.service;

import com.n4monitor.dto.PermissionFormDto;
import com.n4monitor.dto.PermissionTreeDto;
import com.n4monitor.entity.SysPermission;
import com.n4monitor.exception.BusinessException;
import com.n4monitor.mapper.SysPermissionMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SysPermissionService {
    private final SysPermissionMapper permissionMapper;

    public SysPermissionService(SysPermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    public List<PermissionTreeDto> tree() {
        return buildTree(permissionMapper.findAll(null));
    }

    public List<PermissionTreeDto> menuTreeByUserId(Long userId) {
        return buildTree(permissionMapper.findByUserId(userId).stream().filter(p -> p.getPermType() == 1).toList());
    }

    public List<String> codesByUserId(Long userId) {
        return permissionMapper.findCodesByUserId(userId);
    }

    public void create(PermissionFormDto form) {
        SysPermission permission = toEntity(form);
        permissionMapper.insert(permission);
    }

    public void update(Long permId, PermissionFormDto form) {
        SysPermission permission = toEntity(form);
        permission.setPermId(permId);
        permissionMapper.update(permission);
    }

    public void delete(Long permId) {
        if (permissionMapper.countGroupRefs(permId) > 0) {
            throw new BusinessException("Permission is referenced by user groups and cannot be deleted");
        }
        permissionMapper.logicalDelete(permId);
    }

    private SysPermission toEntity(PermissionFormDto form) {
        SysPermission permission = new SysPermission();
        permission.setPermName(form.getPermName());
        permission.setPermCode(form.getPermCode());
        permission.setPermType(form.getPermType());
        permission.setParentId(form.getParentId() == null ? 0L : form.getParentId());
        permission.setPath(form.getPath());
        permission.setComponent(form.getComponent());
        permission.setIcon(form.getIcon());
        permission.setSortOrder(form.getSortOrder() == null ? 0 : form.getSortOrder());
        permission.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        return permission;
    }

    private List<PermissionTreeDto> buildTree(List<SysPermission> permissions) {
        Map<Long, PermissionTreeDto> map = new LinkedHashMap<>();
        permissions.stream()
                .sorted(Comparator.comparing(SysPermission::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .forEach(permission -> map.put(permission.getPermId(), toTreeDto(permission)));
        List<PermissionTreeDto> roots = new ArrayList<>();
        for (PermissionTreeDto node : map.values()) {
            if (node.getParentId() == null || node.getParentId() == 0 || !map.containsKey(node.getParentId())) {
                roots.add(node);
            } else {
                map.get(node.getParentId()).getChildren().add(node);
            }
        }
        return roots;
    }

    private PermissionTreeDto toTreeDto(SysPermission permission) {
        PermissionTreeDto dto = new PermissionTreeDto();
        dto.setPermId(permission.getPermId());
        dto.setPermName(permission.getPermName());
        dto.setPermCode(permission.getPermCode());
        dto.setPermType(permission.getPermType());
        dto.setParentId(permission.getParentId());
        dto.setPath(permission.getPath());
        dto.setComponent(permission.getComponent());
        dto.setIcon(permission.getIcon());
        dto.setSortOrder(permission.getSortOrder());
        dto.setStatus(permission.getStatus());
        return dto;
    }
}
