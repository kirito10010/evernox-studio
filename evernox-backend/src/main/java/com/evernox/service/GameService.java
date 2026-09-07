package com.evernox.service;

import com.evernox.dto.GameActionRequest;
import com.evernox.dto.GameMapResponse;
import com.evernox.dto.GameRankItemDto;

import java.util.List;

/**
 * 沙盘争霸游戏服务
 */
public interface GameService {

    /** 获取当前地图全量状态与我的参赛状态 */
    GameMapResponse map(Long userId);

    /** 选底盘加入本轮（含中途加入补偿） */
    void join(Long userId, Long baseCityId);

    /** 执行一次操作（占领/进攻/驻防/建设） */
    void act(Long userId, GameActionRequest request);

    /** 排行榜（进行中实时势力榜 / 结算后正式榜） */
    List<GameRankItemDto> rank(Long userId);

    /** 结算当前进行中的轮次并发放奖励 */
    void settle();

    /** 开启新一轮（地图重置） */
    void startRound();
}
