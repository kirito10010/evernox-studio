package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.ReleaseAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 版本附件 Mapper
 */
@Mapper
public interface ReleaseAssetRepository extends BaseMapper<ReleaseAsset> {

    /**
     * 公开下载解析：同一项目下可能存在跨版本同名文件，
     * 按“最新已发布版本优先”返回。
     */
    @Select("SELECT a.* FROM release_asset a " +
            "JOIN project_release r ON a.release_id = r.id " +
            "JOIN release_project p ON r.project_id = p.id " +
            "WHERE p.slug = #{slug} AND a.file_name = #{fileName} " +
            "AND p.deleted = 0 AND r.deleted = 0 AND a.deleted = 0 AND r.status = 1 " +
            "ORDER BY r.published_at DESC, r.id DESC LIMIT 1")
    ReleaseAsset findPublicAsset(@Param("slug") String slug, @Param("fileName") String fileName);

    @Select("SELECT * FROM release_asset WHERE upload_id = #{uploadId} AND deleted = 0 LIMIT 1")
    ReleaseAsset findByUploadId(@Param("uploadId") String uploadId);

    @Select("SELECT COUNT(*) FROM release_asset WHERE project_id = #{projectId} AND deleted = 0")
    long countByProject(@Param("projectId") Long projectId);

    @Select("SELECT COALESCE(SUM(`size`), 0) FROM release_asset WHERE project_id = #{projectId} AND deleted = 0")
    long sumSizeByProject(@Param("projectId") Long projectId);

    @Select("SELECT COALESCE(SUM(download_count), 0) FROM release_asset WHERE project_id = #{projectId} AND deleted = 0")
    long sumDownloadsByProject(@Param("projectId") Long projectId);

    @Select("SELECT COUNT(*) FROM release_asset WHERE deleted = 0")
    long countActive();

    @Select("SELECT COALESCE(SUM(`size`), 0) FROM release_asset WHERE deleted = 0")
    long sumSizeActive();

    @Select("SELECT COALESCE(SUM(download_count), 0) FROM release_asset WHERE deleted = 0")
    long sumDownloadsActive();

    @Update("UPDATE release_asset SET download_count = download_count + 1 WHERE id = #{id}")
    int incrementDownload(@Param("id") Long id);

    @Select("SELECT * FROM release_asset WHERE release_id IS NULL AND deleted = 0 AND created_at < #{before}")
    List<ReleaseAsset> findStagedBefore(@Param("before") LocalDateTime before);
}
