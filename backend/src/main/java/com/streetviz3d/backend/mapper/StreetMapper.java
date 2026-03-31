package com.streetviz3d.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.streetviz3d.backend.entity.Street;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StreetMapper extends BaseMapper<Street> {
}