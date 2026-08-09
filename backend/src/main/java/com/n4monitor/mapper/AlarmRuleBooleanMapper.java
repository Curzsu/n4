package com.n4monitor.mapper;

import com.n4monitor.entity.AlarmRuleBooleanEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AlarmRuleBooleanMapper {

    List<AlarmRuleBooleanEntity> findAll();

    AlarmRuleBooleanEntity findById(@Param("id") Long id);

    List<String> findAlarmNamesByPointNamePrefix(@Param("prefix") String prefix);

    int insert(AlarmRuleBooleanEntity entity);

    int update(AlarmRuleBooleanEntity entity);

    int deleteById(@Param("id") Long id);
}
