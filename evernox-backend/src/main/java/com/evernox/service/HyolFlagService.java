package com.evernox.service;

import com.evernox.dto.HyolFlagImportResponse;
import com.evernox.dto.HyolFlagItemResponse;
import com.evernox.dto.HyolFlagPeriodResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 火影忍者OL 活动详情 · 彩旗献礼服务
 */
public interface HyolFlagService {

    /** 期列表（最新在前），带本期商品件数 */
    List<HyolFlagPeriodResponse> periods();

    /** 某期全部商品 */
    List<HyolFlagItemResponse> items(Long periodId);

    /**
     * 整期导入：JSON + 商品图标。
     *
     * <p>期由 JSON 内的「活动.开始/结束」推导，调用方不用指定；重复导入按整期覆盖。
     */
    HyolFlagImportResponse importFolder(MultipartFile manifest, List<MultipartFile> images);

    /** 删除整期（含全部商品） */
    void deletePeriod(Long periodId);
}
