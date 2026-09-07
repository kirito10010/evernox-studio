package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 沙盘争霸城市邻接关系（静态表，种子时按中心距离计算）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("game_city_adjacency")
public class GameCityAdjacency {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long cityId;

    private Long adjacentCityId;
}
