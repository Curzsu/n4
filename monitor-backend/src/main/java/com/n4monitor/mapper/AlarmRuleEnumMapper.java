package com.n4monitor.mapper;

import com.n4monitor.entity.AlarmRuleEnumEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AlarmRuleEnumMapper {

    List<AlarmRuleEnumEntity> findAll();

    /** Find all enum rows for a given alarm_name (one point = multiple enum values). */
    List<AlarmRuleEnumEntity> findByAlarmName(@Param("alarmName") String alarmName);

    List<String> findAlarmNamesByPointNamePrefix(@Param("prefix") String prefix);

    int insert(AlarmRuleEnumEntity entity);

    int update(AlarmRuleEnumEntity entity);

    /** Soft-delete all rows with the given alarm_name (deletes all enum values of a point). */
    int deleteByAlarmName(@Param("alarmName") String alarmName);

    int deleteById(@Param("id") Long id);
}
