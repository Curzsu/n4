package com.n4monitor.mapper;

import com.n4monitor.entity.PointDeploymentEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PointDeploymentMapper {

    /** 查询所有未删除点位的 path + alarm_code，用于同步规则映射到 Redis */
    List<PointDeploymentEntity> findAllForRuleMap();
}
