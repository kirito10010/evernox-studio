package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.AiNewsItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiNewsItemRepository extends BaseMapper<AiNewsItem> {
}
