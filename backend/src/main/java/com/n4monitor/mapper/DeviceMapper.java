package com.n4monitor.mapper;

import com.n4monitor.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceMapper {

    List<Device> findAll();

    List<Device> findBySysParentId(@Param("sysParentId") Long sysParentId);

    List<Device> findRootDevices();

    /** Count devices grouped by product type for system status */
    List<Device> countByProductType();

    Device findByDeviceId(@Param("deviceId") Long deviceId);

    Device findByDeviceCode(@Param("deviceCode") String deviceCode);

    Device findByDeviceName(@Param("deviceName") String deviceName);
}
