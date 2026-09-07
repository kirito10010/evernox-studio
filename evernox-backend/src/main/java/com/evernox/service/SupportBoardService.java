package com.evernox.service;

import com.evernox.dto.SupportBoardResponse;
import com.evernox.dto.SupportBoardViewResponse;
import com.evernox.dto.SupportPixelRequest;

import java.util.List;

/**
 * 应援板服务
 */
public interface SupportBoardService {

    /** 当前展示画板 + 所有像素 */
    SupportBoardViewResponse getActive();

    /** 绘制/擦除像素（作用于当前展示画板） */
    void setPixel(Long userId, SupportPixelRequest request);

    /** 管理员锁定像素（作用于当前展示画板） */
    void lockPixel(Long adminId, int x, int y);

    /** 管理员解锁像素（作用于当前展示画板） */
    void unlockPixel(int x, int y);

    /** 管理员批量锁定像素（作用于当前展示画板） */
    void lockPixels(Long adminId, List<SupportPixelRequest> pixels);

    /** 管理员批量解锁像素（作用于当前展示画板） */
    void unlockPixels(List<SupportPixelRequest> pixels);

    List<SupportBoardResponse> list();

    SupportBoardResponse create(String name);

    void setActive(Long id);

    void delete(Long id);
}
