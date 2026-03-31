package com.streetviz3d.backend.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.streetviz3d.backend.entity.Segment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SegmentMapper extends BaseMapper<Segment> {
}