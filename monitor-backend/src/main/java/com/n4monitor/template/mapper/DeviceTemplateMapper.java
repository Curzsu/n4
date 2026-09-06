package com.n4monitor.template.mapper;

import com.n4monitor.template.entity.DeviceTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceTemplateMapper {

    List<DeviceTemplate> findAllWithPointCount();

    List<DeviceTemplate> findAllIncludingDeleted();

    DeviceTemplate findByDeviceTmplId(@Param("deviceTmplId") Long deviceTmplId);

    int insert(DeviceTemplate template);

    int update(DeviceTemplate template);

    int logicalDelete(@Param("deviceTmplId") Long deviceTmplId);

    int restore(@Param("deviceTmplId") Long deviceTmplId);

    int updateAlarmCode(@Param("deviceTmplId") Long deviceTmplId, @Param("alarmCode") String alarmCode);
}
