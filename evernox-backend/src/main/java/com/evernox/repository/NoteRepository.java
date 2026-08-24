package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.Note;
import org.apache.ibatis.annotations.Mapper;

/**
 * 记事本 Mapper
 */
@Mapper
public interface NoteRepository extends BaseMapper<Note> {
}
