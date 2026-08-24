package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.Album;
import org.apache.ibatis.annotations.Mapper;

/**
 * 相册 Mapper
 */
@Mapper
public interface AlbumRepository extends BaseMapper<Album> {
}
