package com.n4monitor.deployment.mapper;

import com.n4monitor.deployment.entity.PointDeploymentEntity;
import com.n4monitor.deployment.entity.PointDeployment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PointDeploymentMapper {

    /** Query path + alarm_code for all non-deleted points, used to sync the rule mapping to Redis */
    List<PointDeploymentEntity> findAllForRuleMap();

    List<PointDeployment> findAllIncludingDeleted();

    List<PointDeployment> findAllByN4Addresses(@Param("n4Addresses") List<String> n4Addresses);

    List<PointDeployment> findByDeviceDeployId(@Param("deviceDeployId") Long deviceDeployId);

    PointDeployment findByPointDeployId(@Param("pointDeployId") Long pointDeployId);

    int insert(PointDeployment point);

    int insertBatch(@Param("points") List<PointDeployment> points);

    int update(PointDeployment point);

    int updateNamePrefix(@Param("deviceDeployId") Long deviceDeployId,
                         @Param("oldPrefix") String oldPrefix,
                         @Param("newPrefix") String newPrefix,
                         @Param("nodeName") String nodeName);

    int deleteByDeviceDeployId(@Param("deviceDeployId") Long deviceDeployId);

    int restore(@Param("pointDeployId") Long pointDeployId);

    int updateImportedMetadata(PointDeployment point);

    int updateAlarmCodesBatch(@Param("points") List<PointDeployment> points);
}
