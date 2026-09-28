package com.n4monitor.deployment.mapper;

import com.n4monitor.deployment.entity.DeviceDeployment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceDeploymentMapper {

    List<DeviceDeployment> findAll();

    List<DeviceDeployment> findAllIncludingDeleted();

    List<DeviceDeployment> findByNavNodeId(@Param("navNodeId") Long navNodeId);

    DeviceDeployment findByDeviceDeployId(@Param("deviceDeployId") Long deviceDeployId);

    int insert(DeviceDeployment deployment);

    int update(DeviceDeployment deployment);

    int releaseUnmountedNameConflict(@Param("systemName") String systemName,
                                     @Param("deviceDeployName") String deviceDeployName);

    int updateNode(@Param("deviceDeployId") Long deviceDeployId,
                   @Param("navNodeId") Long navNodeId,
                   @Param("deviceDeployName") String deviceDeployName,
                   @Param("deviceAlias") String deviceAlias,
                   @Param("n4Address") String n4Address);

    int logicalDelete(@Param("deviceDeployId") Long deviceDeployId);

    int restore(@Param("deviceDeployId") Long deviceDeployId);

    int updateAlarmCode(@Param("deviceDeployId") Long deviceDeployId, @Param("alarmCode") String alarmCode);

    Long findMaxDeviceIdByNavNodeId(@Param("navNodeId") Long navNodeId);

    Long findMaxDeviceId();

    int updateDeviceId(@Param("oldDeviceId") Long oldDeviceId, @Param("newDeviceId") Long newDeviceId);
}
