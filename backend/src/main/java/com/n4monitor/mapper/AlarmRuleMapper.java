package com.n4monitor.mapper;

import com.n4monitor.entity.AlarmRuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AlarmRuleMapper {

    List<AlarmRuleEntity> findAll();

    AlarmRuleEntity findById(@Param("id") Long id);

    /**
     * Find all alarm_name values (non-deleted) that start with the given pointName prefix
     * followed by "_". Used to determine the next sequence number for a new rule.
     */
    List<String> findAlarmNamesByPointNamePrefix(@Param("prefix") String prefix);

    int insert(AlarmRuleEntity entity);

    int update(AlarmRuleEntity entity);

    int deleteById(@Param("id") Long id);
}
