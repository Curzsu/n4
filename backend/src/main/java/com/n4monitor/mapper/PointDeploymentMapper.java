package com.n4monitor.mapper;

import com.n4monitor.entity.PointDeploymentEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PointDeploymentMapper {

    /** Query path + alarm_code for all non-deleted points, used to sync the rule mapping to Redis */
    List<PointDeploymentEntity> findAllForRuleMap();
}
