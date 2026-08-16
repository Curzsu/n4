package com.n4monitor.mapper;

import com.n4monitor.entity.DeviceDeployment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceDeploymentMapper {

    List<DeviceDeployment> findAll();

    List<DeviceDeployment> findByNavNodeId(@Param("navNodeId") Long navNodeId);

    DeviceDeployment findByDeviceDeployId(@Param("deviceDeployId") Long deviceDeployId);

    int insert(DeviceDeployment deployment);

    int update(DeviceDeployment deployment);

    int updateNode(@Param("deviceDeployId") Long deviceDeployId,
                   @Param("navNodeId") Long navNodeId,
                   @Param("deviceDeployName") String deviceDeployName,
                   @Param("deviceAlias") String deviceAlias,
                   @Param("n4Address") String n4Address);

    int logicalDelete(@Param("deviceDeployId") Long deviceDeployId);
}
