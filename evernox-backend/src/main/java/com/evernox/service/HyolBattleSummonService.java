package com.evernox.service;

import com.evernox.dto.HyolBattleImportResponse;
import com.evernox.dto.HyolBattleItemResponse;
import com.evernox.dto.HyolBattlePeriodResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 火影忍者OL 活动详情 · 战场的召唤服务
 */
public interface HyolBattleSummonService {

    /** 期列表（最新在前），带阵营概览与件数 */
    List<HyolBattlePeriodResponse> periods();

    /** 某期全部商品（含阵营） */
    List<HyolBattleItemResponse> items(Long periodId);

    /** 整期导入：按「活动.结束」定期，重复导入按整期覆盖 */
    HyolBattleImportResponse importFolder(MultipartFile manifest, List<MultipartFile> images);

    /** 删除整期（含阵营与商品） */
    void deletePeriod(Long periodId);
}
