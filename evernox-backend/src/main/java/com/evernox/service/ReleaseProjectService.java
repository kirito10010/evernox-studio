package com.evernox.service;

import com.evernox.dto.ReleaseProjectRequest;
import com.evernox.dto.ReleaseProjectResponse;

import java.util.List;

/**
 * 发布项目服务
 */
public interface ReleaseProjectService {

    List<ReleaseProjectResponse> list(String keyword, Long ownerId);

    ReleaseProjectResponse get(Long id, Long ownerId);

    ReleaseProjectResponse create(ReleaseProjectRequest request, Long ownerId);

    ReleaseProjectResponse update(Long id, ReleaseProjectRequest request, Long ownerId);

    /**
     * 级联删除项目下的版本、附件与磁盘文件。
     *
     * @param confirmSlug 必须与项目 slug 完全一致
     */
    void delete(Long id, String confirmSlug, Long ownerId);

}
