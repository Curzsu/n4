package com.n4monitor.mapper;

import com.n4monitor.dto.OperationLogDto;
import com.n4monitor.entity.SysOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SysOperationLogMapper {

    int insert(SysOperationLog log);

    List<OperationLogDto> findPage(@Param("operatorName") String operatorName,
                                   @Param("module") String module,
                                   @Param("operation") String operation,
                                   @Param("status") Integer status,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime,
                                   @Param("offset") int offset,
                                   @Param("pageSize") int pageSize);

    long countPage(@Param("operatorName") String operatorName,
                   @Param("module") String module,
                   @Param("operation") String operation,
                   @Param("status") Integer status,
                   @Param("startTime") LocalDateTime startTime,
                   @Param("endTime") LocalDateTime endTime);

    OperationLogDto findById(@Param("logId") Long logId);

    int deleteByIds(@Param("ids") List<Long> ids);

    int clear();
}
