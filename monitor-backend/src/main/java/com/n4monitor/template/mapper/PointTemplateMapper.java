package com.n4monitor.template.mapper;

import com.n4monitor.template.entity.PointTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PointTemplateMapper {

    List<PointTemplate> findByDeviceTmplId(@Param("deviceTmplId") Long deviceTmplId);

    PointTemplate findByPointTmplId(@Param("pointTmplId") Long pointTmplId);

    int insert(PointTemplate point);

    int update(PointTemplate point);

    int logicalDelete(@Param("pointTmplId") Long pointTmplId);

    int countByDeviceTmplId(@Param("deviceTmplId") Long deviceTmplId);
}
