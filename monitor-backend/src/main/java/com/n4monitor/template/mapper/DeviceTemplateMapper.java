package com.n4monitor.template.mapper;

import com.n4monitor.template.entity.DeviceTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceTemplateMapper {

    List<DeviceTemplate> findAllWithPointCount();

    DeviceTemplate findByDeviceTmplId(@Param("deviceTmplId") Long deviceTmplId);

    int insert(DeviceTemplate template);

    int update(DeviceTemplate template);

    int logicalDelete(@Param("deviceTmplId") Long deviceTmplId);
}
