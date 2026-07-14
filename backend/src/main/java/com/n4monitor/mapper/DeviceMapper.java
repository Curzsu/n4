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
    void insert(Device device);
    void update(Device device);
    void logicalDelete(@Param("deviceId") Long deviceId);
}
