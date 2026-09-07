package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 沙盘争霸城市（静态表，启动时种子填充）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("game_city")
public class GameCity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 行政区划代码，全局唯一 */
    private String adcode;

    /** 城市名 */
    private String name;

    /** 所属省份 */
    private String province;

    /** 中心经度 */
    private Double centerLng;

    /** 中心纬度 */
    private Double centerLat;

    /** 基础权重（越大越值钱） */
    private Integer weight;

    /** 基础守备力 */
    private Integer baseDefense;

    private LocalDateTime createdAt;
}
