package com.n4monitor.template.mapper;

import com.n4monitor.template.entity.PointTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PointTemplateMapper {

    List<PointTemplate> findByDeviceTmplId(@Param("deviceTmplId") Long deviceTmplId);

    List<PointTemplate> findAllIncludingDeleted();

    PointTemplate findByPointTmplId(@Param("pointTmplId") Long pointTmplId);

    int insert(PointTemplate point);

    int update(PointTemplate point);

    int logicalDelete(@Param("pointTmplId") Long pointTmplId);

    int restore(@Param("pointTmplId") Long pointTmplId);

    int updateTypeAndUnitIfEmpty(PointTemplate point);

    int updateAlarmCode(@Param("pointTmplId") Long pointTmplId, @Param("alarmCode") String alarmCode);

    int updateAlarmCodesBatch(@Param("points") List<PointTemplate> points);

    int countByDeviceTmplId(@Param("deviceTmplId") Long deviceTmplId);
}
