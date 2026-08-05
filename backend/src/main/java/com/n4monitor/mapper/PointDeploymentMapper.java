package com.n4monitor.mapper;

import com.n4monitor.entity.PointDeployment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PointDeploymentMapper {

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
}
