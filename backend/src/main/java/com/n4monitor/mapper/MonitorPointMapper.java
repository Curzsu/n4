package com.n4monitor.mapper;

import com.n4monitor.entity.MonitorPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MonitorPointMapper {

    List<MonitorPoint> findByDeviceId(@Param("deviceId") Long deviceId);

    List<MonitorPoint> findAll();

    /** Count points by status (1=normal, 0=alarm) */
    int countByPointStatus(@Param("pointStatus") Integer pointStatus);
}
