package com.evernox.service;

import com.evernox.dto.HyolMallImportResponse;
import com.evernox.dto.HyolMallItemResponse;
import com.evernox.dto.HyolMallPeriodResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 火影忍者OL 活动详情 · 商城特惠服务
 */
public interface HyolMallService {

    /** 期列表（最新在前），每期带 7 天逐日概览 */
    List<HyolMallPeriodResponse> periods();

    /**
     * 某期商品。
     *
     * @param periodId 期 ID
     * @param day      归属日 yyyy-MM-dd，为空表示整期
     */
    List<HyolMallItemResponse> items(Long periodId, String day);

    /**
     * 导入某一天的数据（JSON + 商品图标）。
     *
     * 期与归属日全部由 JSON 内的「活动.开始/结束」「当日窗口.开始」推导，调用方不用指定。
     */
    HyolMallImportResponse importFolder(MultipartFile manifest, List<MultipartFile> images);

    /** 删除整期（含该期所有商品） */
    void deletePeriod(Long periodId);

    /**
     * 删除某一天的导入数据。
     *
     * 若删完后该期已无任何商品，期记录一并删除，避免下拉框里留下 0 件的空期。
     */
    void deleteDay(Long periodId, String day);
}
