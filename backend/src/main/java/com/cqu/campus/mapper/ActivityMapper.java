package com.cqu.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqu.campus.domain.entity.Activity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ActivityMapper extends BaseMapper<Activity> {

    /**
     * 事务内锁定活动行，保证名额判定串行化
     */
    @Select("SELECT * FROM activities WHERE id = #{id} FOR UPDATE")
    Activity selectForUpdate(@Param("id") Long id);
}
