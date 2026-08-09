package com.n4monitor.mapper;

import com.n4monitor.entity.AlarmRuleNumericEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AlarmRuleNumericMapper {

    List<AlarmRuleNumericEntity> findAll();

    AlarmRuleNumericEntity findById(@Param("id") Long id);

    List<String> findAlarmNamesByPointNamePrefix(@Param("prefix") String prefix);

    int insert(AlarmRuleNumericEntity entity);

    int update(AlarmRuleNumericEntity entity);

    int deleteById(@Param("id") Long id);
}
