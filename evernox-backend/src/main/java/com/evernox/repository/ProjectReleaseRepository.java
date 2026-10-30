package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.ProjectRelease;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 版本发布 Mapper
 */
@Mapper
public interface ProjectReleaseRepository extends BaseMapper<ProjectRelease> {

    @Select("SELECT COUNT(*) FROM project_release WHERE project_id = #{projectId} AND deleted = 0")
    long countByProject(@Param("projectId") Long projectId);

    @Select("SELECT COUNT(*) FROM project_release WHERE project_id = #{projectId} AND status = 1 AND deleted = 0")
    long countPublishedByProject(@Param("projectId") Long projectId);

    @Select("SELECT * FROM project_release WHERE project_id = #{projectId} AND deleted = 0 " +
            "AND status = 1 AND is_latest = 1 ORDER BY id DESC LIMIT 1")
    ProjectRelease findLatestByProject(@Param("projectId") Long projectId);

    @Select("SELECT COUNT(*) FROM project_release WHERE deleted = 0")
    long countActive();

    @Select("SELECT COUNT(*) FROM project_release WHERE status = 1 AND deleted = 0")
    long countPublished();

    @Update("UPDATE project_release SET is_latest = 0 WHERE project_id = #{projectId} AND deleted = 0")
    int clearLatest(@Param("projectId") Long projectId);

    @Select("SELECT COALESCE(SUM(download_count), 0) FROM project_release " +
            "WHERE project_id = #{projectId} AND deleted = 0")
    long sumDownloadsByProject(@Param("projectId") Long projectId);

    /** 下载计数不更新 updated_at，避免每次下载都改变版本排序时间 */
    @Update("UPDATE project_release SET download_count = download_count + 1, " +
            "updated_at = updated_at WHERE id = #{id}")
    int incrementDownload(@Param("id") Long id);
}

