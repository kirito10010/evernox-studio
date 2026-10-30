package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.ReleaseProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 发布项目 Mapper
 */
@Mapper
public interface ReleaseProjectRepository extends BaseMapper<ReleaseProject> {

    @Select("SELECT COUNT(*) FROM release_project WHERE deleted = 0")
    long countActive();
}
