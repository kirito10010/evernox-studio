package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.NoteImage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 笔记-插图关联 Mapper
 */
@Mapper
public interface NoteImageRepository extends BaseMapper<NoteImage> {
}
