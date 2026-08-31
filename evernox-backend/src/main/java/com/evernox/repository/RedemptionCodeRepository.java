package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.RedemptionCode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 超级会员卡密数据访问层
 */
@Mapper
public interface RedemptionCodeRepository extends BaseMapper<RedemptionCode> {
}
