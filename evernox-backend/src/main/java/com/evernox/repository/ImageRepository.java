package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.Image;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图片 Mapper
 */
@Mapper
public interface ImageRepository extends BaseMapper<Image> {
}
