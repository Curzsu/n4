package com.n4monitor.service;

import com.n4monitor.dto.IdsRequest;
import com.n4monitor.dto.OperationLogDto;
import com.n4monitor.dto.PageResult;
import com.n4monitor.entity.SysOperationLog;
import com.n4monitor.mapper.SysOperationLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SysOperationLogService {
    private final SysOperationLogMapper logMapper;

    public SysOperationLogService(SysOperationLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    public void record(SysOperationLog log) {
        logMapper.insert(log);
    }

    public PageResult<OperationLogDto> page(String operatorName, String module, String operation, Integer status,
                                            LocalDateTime startTime, LocalDateTime endTime, int page, int pageSize) {
        int currentPage = Math.max(page, 1);
        int currentPageSize = Math.max(pageSize, 1);
        int offset = (currentPage - 1) * currentPageSize;
        List<OperationLogDto> records = logMapper.findPage(operatorName, module, operation, status, startTime, endTime, offset, currentPageSize);
        long total = logMapper.countPage(operatorName, module, operation, status, startTime, endTime);
        return new PageResult<>(records, total, currentPage, currentPageSize);
    }

    public OperationLogDto detail(Long logId) {
        return logMapper.findById(logId);
    }

    public void delete(IdsRequest request) {
        if (request != null && request.getIds() != null && !request.getIds().isEmpty()) {
            logMapper.deleteByIds(request.getIds());
        }
    }

    public void clear() {
        logMapper.clear();
    }
}
