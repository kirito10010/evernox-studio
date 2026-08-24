package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.ImageAlbum;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图片-相册关联 Mapper
 */
@Mapper
public interface ImageAlbumRepository extends BaseMapper<ImageAlbum> {
}
