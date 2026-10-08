package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.evernox.dto.GameActionRequest;
import com.evernox.dto.GameMapChangeEvent;
import com.evernox.dto.GameMapResponse;
import com.evernox.dto.GameRankItemDto;
import com.evernox.entity.GameCity;
import com.evernox.entity.GameCityAdjacency;
import com.evernox.entity.GameCityOwner;
import com.evernox.entity.GamePlayer;
import com.evernox.entity.GameReward;
import com.evernox.entity.GameRound;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.repository.GameCityAdjacencyRepository;
import com.evernox.repository.GameCityOwnerRepository;
import com.evernox.repository.GameCityRepository;
import com.evernox.repository.GamePlayerRepository;
import com.evernox.repository.GameRewardRepository;
import com.evernox.repository.GameRoundRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.GameService;
import com.evernox.service.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 沙盘争霸游戏服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class GameServiceImpl implements GameService {

    private static final int HOURLY_FORCE_BASE = 40;
    private static final int CITY_FORCE_PER_LEVEL_PER_HOUR = 3;
    private static final int MAX_ACTION_POINTS = 20;
    /** 离线最多累积的小时数，超过不补发，鼓励频繁上线 */
    private static final int MAX_ACCUMULATED_HOURS = 6;
    private static final int JOIN_BASE_FORCE = 800;
    private static final int LATE_JOIN_FORCE_PER_DAY = 600;
    private static final int DEFENSE_LEVEL_BONUS = 10;
    private static final int DEVELOP_COST_BASE = 50;
    private static final double SCORE_LEVEL_COEF = 0.2;
    /** 完整占领一个省（该省所有城市）的势力加成 */
    private static final int COMPLETE_PROVINCE_BONUS = 5;

    private final GameCityRepository cityRepository;
    private final GameCityAdjacencyRepository adjacencyRepository;
    private final GameRoundRepository roundRepository;
    private final GamePlayerRepository playerRepository;
    private final GameCityOwnerRepository ownerRepository;
    private final GameRewardRepository rewardRepository;
    private final UserRepository userRepository;
    private final PointsService pointsService;
    private final GameSseRegistry sseRegistry;

    // ==================== 查询 ====================

    @Override
    public GameMapResponse map(Long userId) {
        GameRound running = runningRound();
        GameRound round = running != null ? running : latestRound();
        if (round == null) {
            return GameMapResponse.builder()
                    .roundStatus(-1)
                    .joined(false)
                    .cities(List.of())
                    .build();
        }

        List<GameCity> cities = cityRepository.selectList(null);
        Map<Long, GameCityOwner> ownerMap = ownerRepository.selectList(
                        new LambdaQueryWrapper<GameCityOwner>().eq(GameCityOwner::getRoundId, round.getId()))
                .stream().collect(Collectors.toMap(GameCityOwner::getCityId, Function.identity()));

        GamePlayer player = findPlayer(round.getId(), userId);
        if (player != null) {
            grantHourlyIfNeeded(player);
        }

        List<GameMapResponse.CityState> states = cities.stream().map(c -> {
            GameCityOwner o = ownerMap.get(c.getId());
            return GameMapResponse.CityState.builder()
                    .id(c.getId())
                    .adcode(c.getAdcode())
                    .name(c.getName())
                    .province(c.getProvince())
                    .centerLng(c.getCenterLng())
                    .centerLat(c.getCenterLat())
                    .weight(c.getWeight())
                    .baseDefense(c.getBaseDefense())
                    .ownerUserId(o == null ? null : o.getOwnerUserId())
                    .level(o == null ? 0 : o.getLevel())
                    .garrison(o == null ? 0 : o.getGarrison())
                    .build();
        }).toList();

        int ownedCount = 0;
        int completedProvinces = 0;
        double powerScore = 0;
        if (player != null) {
            List<GameCityOwner> owned = ownedCities(round.getId(), userId);
            ownedCount = owned.size();
            Map<Long, GameCity> cityMap = cities.stream().collect(Collectors.toMap(GameCity::getId, Function.identity()));
            powerScore = powerScore(owned, cityMap);
            Set<Long> ownedIds = owned.stream().map(GameCityOwner::getCityId).collect(Collectors.toSet());
            completedProvinces = completedProvinceCount(cityMap, ownedIds);
        }

        return GameMapResponse.builder()
                .roundId(round.getId())
                .roundNo(round.getRoundNo())
                .roundStatus(round.getStatus() != null && round.getStatus() == GameRound.STATUS_RUNNING && isSettled(round)
                        ? GameRound.STATUS_SETTLED
                        : round.getStatus())
                .startAt(round.getStartAt())
                .settleAt(round.getSettleAt())
                .endAt(round.getEndAt())
                .joined(player != null)
                .baseCityId(player == null ? null : player.getBaseCityId())
                .force(player == null ? 0 : player.getForce())
                .actionPoints(player == null ? 0 : player.getActionPoints())
                .ownedCityCount(ownedCount)
                .completedProvinces(completedProvinces)
                .powerScore(powerScore)
                .cities(states)
                .build();
    }

    @Override
    public List<GameRankItemDto> rank(Long userId) {
        GameRound round = runningRound() != null ? runningRound() : latestRound();
        if (round == null) {
            return List.of();
        }
        List<GameCity> cities = cityRepository.selectList(null);
        Map<Long, GameCity> cityMap = cities.stream().collect(Collectors.toMap(GameCity::getId, Function.identity()));
        List<GamePlayer> players = playerRepository.selectList(
                new LambdaQueryWrapper<GamePlayer>().eq(GamePlayer::getRoundId, round.getId()));
        Map<Long, String> names = loadUsernames(players);

        List<GameRankItemDto> result = new ArrayList<>();
        int rank = 0;
        for (ScoredPlayer sp : scoreAndRank(round.getId(), players, cityMap)) {
            rank++;
            result.add(GameRankItemDto.builder()
                    .rank(rank)
                    .userId(sp.player().getUserId())
                    .username(names.get(sp.player().getUserId()))
                    .ownedCityCount(sp.ownedCityCount())
                    .force(sp.player().getForce())
                    .powerScore(sp.score())
                    .build());
        }
        return result;
    }

    // ==================== 加入与操作 ====================

    @Override
    @Transactional
    public void join(Long userId, Long baseCityId) {
        GameRound round = requireRunningRound();
        if (findPlayer(round.getId(), userId) != null) {
            throw new BusinessException("你已加入本轮");
        }
        GameCity city = cityRepository.selectById(baseCityId);
        if (city == null) {
            throw new BusinessException("城市不存在");
        }
        if (findOwner(round.getId(), baseCityId) != null) {
            throw new BusinessException("该城市已被占领，请选择其他底盘");
        }

        long daysElapsed = Math.max(0, ChronoUnit.DAYS.between(round.getStartAt().toLocalDate(), LocalDate.now()));
        int startingForce = JOIN_BASE_FORCE + (int) (LATE_JOIN_FORCE_PER_DAY * daysElapsed);
        int isNew = daysElapsed > 0 ? 1 : 0;
        LocalDateTime now = LocalDateTime.now();

        GamePlayer player = GamePlayer.builder()
                .roundId(round.getId())
                .userId(userId)
                .baseCityId(baseCityId)
                .force(startingForce)
                .actionPoints(MAX_ACTION_POINTS)
                .isNew(isNew)
                .joinedAt(now)
                .lastGrantAt(now)
                .updatedAt(now)
                .build();
        playerRepository.insert(player);

        insertOwner(round.getId(), baseCityId, userId, 0, 0);
        broadcast(round.getId(), baseCityId, userId, 0, 0);
        log.info("沙盘加入: round={}, userId={}, base={}, force={}, isNew={}",
                round.getId(), userId, baseCityId, startingForce, isNew);
    }

    @Override
    @Transactional
    public void act(Long userId, GameActionRequest request) {
        GameRound round = requireRunningRound();
        GamePlayer player = requirePlayer(round.getId(), userId);
        grantHourlyIfNeeded(player);
        if (player.getActionPoints() == null || player.getActionPoints() <= 0) {
            throw new BusinessException("今日体力已用完");
        }
        if (request.getCityId() == null) {
            throw new BusinessException("请选择目标城市");
        }

        switch (request.getType() == null ? "" : request.getType()) {
            case "occupy" -> occupy(round, player, request.getCityId());
            case "attack" -> attack(round, player, request.getCityId(), request.getForce());
            case "garrison" -> garrison(round, player, request.getCityId(), request.getForce());
            case "develop" -> develop(round, player, request.getCityId());
            default -> throw new BusinessException("未知操作类型");
        }

        player.setActionPoints(player.getActionPoints() - 1);
        player.setUpdatedAt(LocalDateTime.now());
        playerRepository.updateById(player);
    }

    private void occupy(GameRound round, GamePlayer player, Long cityId) {
        GameCity city = requireCity(cityId);
        if (findOwner(round.getId(), cityId) != null) {
            throw new BusinessException("该城市已有归属，不能占领");
        }
        if (!adjacentToMyCities(round.getId(), player.getUserId(), cityId)) {
            throw new BusinessException("只能占领与我方城市相邻的城市");
        }
        int cost = city.getBaseDefense();
        requireForce(player, cost);
        player.setForce(player.getForce() - cost);
        try {
            insertOwner(round.getId(), cityId, player.getUserId(), 0, 0);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该城市刚被他人占领");
        }
        broadcast(round.getId(), cityId, player.getUserId(), 0, 0);
    }

    private void attack(GameRound round, GamePlayer player, Long cityId, Integer committed) {
        GameCity city = requireCity(cityId);
        GameCityOwner owner = findOwner(round.getId(), cityId);
        if (owner == null) {
            throw new BusinessException("该城市无主，应使用占领");
        }
        if (owner.getOwnerUserId().equals(player.getUserId())) {
            throw new BusinessException("不能进攻自己的城市");
        }
        if (!adjacentToMyCities(round.getId(), player.getUserId(), cityId)) {
            throw new BusinessException("只能进攻与我方城市相邻的城市");
        }
        if (isBaseCity(round.getId(), cityId)) {
            throw new BusinessException("该城是某玩家本营，不可进攻");
        }
        int f = committed == null ? 0 : committed;
        if (f <= 0) {
            throw new BusinessException("请投入兵力");
        }
        requireForce(player, f);
        int defense = city.getBaseDefense() + owner.getGarrison() + owner.getLevel() * DEFENSE_LEVEL_BONUS;
        player.setForce(player.getForce() - f);
        if (f > defense) {
            // 占领成功：城归进攻方，等级与驻防清零
            owner.setOwnerUserId(player.getUserId());
            owner.setLevel(0);
            owner.setGarrison(0);
            owner.setCapturedAt(LocalDateTime.now());
            owner.setUpdatedAt(LocalDateTime.now());
            ownerRepository.updateById(owner);
            broadcast(round.getId(), cityId, owner.getOwnerUserId(), owner.getLevel(), owner.getGarrison());
        }
        // 否则进攻失败：只损失投入的兵力，对方无损，无地图变化
    }

    private void garrison(GameRound round, GamePlayer player, Long cityId, Integer amount) {
        requireCity(cityId);
        GameCityOwner owner = findOwner(round.getId(), cityId);
        if (owner == null || !owner.getOwnerUserId().equals(player.getUserId())) {
            throw new BusinessException("只能驻防自己的城市");
        }
        int x = amount == null ? 0 : amount;
        if (x <= 0) {
            throw new BusinessException("请投入驻防兵力");
        }
        requireForce(player, x);
        player.setForce(player.getForce() - x);
        owner.setGarrison(owner.getGarrison() + x);
        owner.setUpdatedAt(LocalDateTime.now());
        ownerRepository.updateById(owner);
        broadcast(round.getId(), cityId, owner.getOwnerUserId(), owner.getLevel(), owner.getGarrison());
    }

    private void develop(GameRound round, GamePlayer player, Long cityId) {
        requireCity(cityId);
        GameCityOwner owner = findOwner(round.getId(), cityId);
        if (owner == null || !owner.getOwnerUserId().equals(player.getUserId())) {
            throw new BusinessException("只能建设自己的城市");
        }
        int cost = DEVELOP_COST_BASE * (owner.getLevel() + 1);
        requireForce(player, cost);
        player.setForce(player.getForce() - cost);
        owner.setLevel(owner.getLevel() + 1);
        owner.setUpdatedAt(LocalDateTime.now());
        ownerRepository.updateById(owner);
        broadcast(round.getId(), cityId, owner.getOwnerUserId(), owner.getLevel(), owner.getGarrison());
    }

    // ==================== 结算与开局 ====================

    @Override
    @Transactional
    public void settle() {
        GameRound round = runningRound();
        if (round == null) {
            return;
        }
        // 条件更新抢占：仅当 status 仍为 RUNNING 时置为 SETTLED，防止并发重复发奖
        int claimed = roundRepository.update(null, new LambdaUpdateWrapper<GameRound>()
                .eq(GameRound::getId, round.getId())
                .eq(GameRound::getStatus, GameRound.STATUS_RUNNING)
                .set(GameRound::getStatus, GameRound.STATUS_SETTLED));
        if (claimed == 0) {
            return; // 已被其他线程结算
        }

        List<GameCity> cities = cityRepository.selectList(null);
        Map<Long, GameCity> cityMap = cities.stream().collect(Collectors.toMap(GameCity::getId, Function.identity()));
        List<GamePlayer> players = playerRepository.selectList(
                new LambdaQueryWrapper<GamePlayer>().eq(GamePlayer::getRoundId, round.getId()));
        if (players.isEmpty()) {
            return; // 已标记 SETTLED，无需发奖
        }

        List<ScoredPlayer> ranked = scoreAndRank(round.getId(), players, cityMap);

        int rookieRank = 0;
        for (int i = 0; i < ranked.size(); i++) {
            ScoredPlayer sp = ranked.get(i);
            GamePlayer p = sp.player();
            int totalRank = i + 1;
            int totalPts = totalBoardPoints(totalRank);

            int rookiePts = 0;
            if (p.getIsNew() != null && p.getIsNew() == 1) {
                rookieRank++;
                rookiePts = rookieBoardPoints(rookieRank);
            }

            int mainPts = Math.max(totalPts, rookiePts);
            String boardType = rookiePts > totalPts ? "rookie" : "total";
            int mainRank = rookiePts > totalPts ? rookieRank : totalRank;

            if (mainPts > 0) {
                pointsService.award(p.getUserId(), mainPts, "沙盘争霸第" + round.getRoundNo() + "轮奖励");
                insertReward(round.getId(), p.getUserId(), mainRank, mainPts, boardType);
            }

            int ownedCount = sp.ownedCityCount();
            if (ownedCount > 0) {
                pointsService.award(p.getUserId(), 10, "沙盘争霸参与奖");
                insertReward(round.getId(), p.getUserId(), 0, 10, "participation");
            }
        }

        log.info("沙盘结算完成: round={}, players={}", round.getId(), ranked.size());
    }

    @Override
    @Transactional
    public void startRound() {
        Integer maxNo = roundRepository.selectList(null).stream()
                .map(GameRound::getRoundNo)
                .max(Integer::compareTo)
                .orElse(0);
        GameRound round = GameRound.builder()
                .roundNo(maxNo + 1)
                .status(GameRound.STATUS_RUNNING)
                .startAt(LocalDateTime.now())
                .settleAt(nextOccurrence(DayOfWeek.SUNDAY, LocalTime.of(18, 0)))
                .endAt(nextOccurrence(DayOfWeek.MONDAY, LocalTime.of(10, 0)))
                .createdAt(LocalDateTime.now())
                .build();
        roundRepository.insert(round);
        log.info("沙盘新轮次: roundNo={}", round.getRoundNo());
    }

    // ==================== 私有工具 ====================

    private void grantHourlyIfNeeded(GamePlayer player) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last = player.getLastGrantAt();
        if (last != null && ChronoUnit.MINUTES.between(last, now) < 60) {
            return;
        }
        long hours = last == null ? 1 : Math.max(1, ChronoUnit.HOURS.between(last, now));
        long capped = Math.min(hours, MAX_ACCUMULATED_HOURS);

        int production = ownedCities(player.getRoundId(), player.getUserId()).stream()
                .mapToInt(o -> o.getLevel() * CITY_FORCE_PER_LEVEL_PER_HOUR)
                .sum();
        int force = (player.getForce() == null ? 0 : player.getForce())
                + (int) ((HOURLY_FORCE_BASE + production) * capped);
        player.setForce(force);

        int ap = (player.getActionPoints() == null ? 0 : player.getActionPoints()) + (int) capped;
        player.setActionPoints(Math.min(ap, MAX_ACTION_POINTS));

        player.setLastGrantAt(now);
        player.setUpdatedAt(now);
        playerRepository.updateById(player);
    }

    /** 玩家的一次性评分结果：排序与结果构建共用，避免重复查库与重复计算 */
    private record ScoredPlayer(GamePlayer player, double score, int ownedCityCount) {}

    /**
     * 一次性为全部玩家算分并排序。
     *
     * 修复前：排序比较器里直接调 powerScore，而 powerScore 内部会查库，
     * 于是比较器被调用多少次就查多少次库（O(N log N) 条 SQL）。
     * 修复后：本轮占领记录只查 1 条 SQL，按 ownerUserId 分组后内存计算。
     */
    private List<ScoredPlayer> scoreAndRank(Long roundId, List<GamePlayer> players,
                                            Map<Long, GameCity> cityMap) {
        Map<Long, List<GameCityOwner>> ownedByUser = ownerRepository.selectList(
                        new LambdaQueryWrapper<GameCityOwner>().eq(GameCityOwner::getRoundId, roundId))
                .stream().collect(Collectors.groupingBy(GameCityOwner::getOwnerUserId));

        List<ScoredPlayer> scored = new ArrayList<>(players.size());
        for (GamePlayer p : players) {
            List<GameCityOwner> owned = ownedByUser.getOrDefault(p.getUserId(), List.of());
            scored.add(new ScoredPlayer(p, powerScore(owned, cityMap), owned.size()));
        }
        // List.sort 是稳定排序，与原先 Stream.sorted 的并列名次行为一致
        scored.sort(Comparator.comparingDouble(ScoredPlayer::score).reversed());
        return scored;
    }

    /**
     * 势力分：只看地盘与等级，避免「囤兵不打」反而排名更高。
     * @param owned 该玩家已占领的城市，由调用方批量预取，避免逐玩家查库
     */
    private double powerScore(List<GameCityOwner> owned, Map<Long, GameCity> cityMap) {
        double score = 0;
        Set<Long> ownedIds = new HashSet<>();
        for (GameCityOwner o : owned) {
            ownedIds.add(o.getCityId());
            GameCity c = cityMap.get(o.getCityId());
            if (c != null) {
                score += c.getWeight() * (1 + o.getLevel() * SCORE_LEVEL_COEF);
            }
        }
        // 兵力不参与势力：势力只看地盘与等级，避免「囤兵不打」反而排名更高
        score += completedProvinceCount(cityMap, ownedIds) * COMPLETE_PROVINCE_BONUS;
        return score;
    }

    /** 统计已完整占领的省数量（该省所有城市均已占领） */
    private int completedProvinceCount(Map<Long, GameCity> cityMap, Set<Long> ownedIds) {
        Map<String, Integer> totalPerProvince = new HashMap<>();
        Map<String, Integer> ownedPerProvince = new HashMap<>();
        for (GameCity c : cityMap.values()) {
            totalPerProvince.merge(c.getProvince(), 1, Integer::sum);
            if (ownedIds.contains(c.getId())) {
                ownedPerProvince.merge(c.getProvince(), 1, Integer::sum);
            }
        }
        int completed = 0;
        for (Map.Entry<String, Integer> e : totalPerProvince.entrySet()) {
            if (ownedPerProvince.getOrDefault(e.getKey(), 0) >= e.getValue()) {
                completed++;
            }
        }
        return completed;
    }

    private boolean adjacentToMyCities(Long roundId, Long userId, Long targetCityId) {
        List<Long> myCityIds = ownedCities(roundId, userId).stream()
                .map(GameCityOwner::getCityId).toList();
        if (myCityIds.isEmpty()) {
            return false;
        }
        Long c = adjacencyRepository.selectCount(new LambdaQueryWrapper<GameCityAdjacency>()
                .in(GameCityAdjacency::getCityId, myCityIds)
                .eq(GameCityAdjacency::getAdjacentCityId, targetCityId));
        return c != null && c > 0;
    }

    private boolean isBaseCity(Long roundId, Long cityId) {
        Long c = playerRepository.selectCount(new LambdaQueryWrapper<GamePlayer>()
                .eq(GamePlayer::getRoundId, roundId)
                .eq(GamePlayer::getBaseCityId, cityId));
        return c != null && c > 0;
    }

    private List<GameCityOwner> ownedCities(Long roundId, Long userId) {
        return ownerRepository.selectList(new LambdaQueryWrapper<GameCityOwner>()
                .eq(GameCityOwner::getRoundId, roundId)
                .eq(GameCityOwner::getOwnerUserId, userId));
    }

    private void insertOwner(Long roundId, Long cityId, Long userId, int level, int garrison) {
        LocalDateTime now = LocalDateTime.now();
        ownerRepository.insert(GameCityOwner.builder()
                .roundId(roundId)
                .cityId(cityId)
                .ownerUserId(userId)
                .level(level)
                .garrison(garrison)
                .capturedAt(now)
                .updatedAt(now)
                .build());
    }

    private void insertReward(Long roundId, Long userId, int rank, int points, String boardType) {
        rewardRepository.insert(GameReward.builder()
                .roundId(roundId)
                .userId(userId)
                .rank(rank)
                .points(points)
                .boardType(boardType)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void broadcast(Long roundId, Long cityId, Long ownerUserId, int level, int garrison) {
        sseRegistry.broadcastMapChange(GameMapChangeEvent.builder()
                .roundId(roundId)
                .cityId(cityId)
                .ownerUserId(ownerUserId)
                .level(level)
                .garrison(garrison)
                .build());
    }

    private void requireForce(GamePlayer player, int cost) {
        if (player.getForce() == null || player.getForce() < cost) {
            throw new BusinessException("兵力不足");
        }
    }

    private GameCity requireCity(Long cityId) {
        GameCity city = cityRepository.selectById(cityId);
        if (city == null) {
            throw new BusinessException("城市不存在");
        }
        return city;
    }

    private GameRound requireRunningRound() {
        GameRound round = runningRound();
        if (round == null) {
            throw new BusinessException("当前无进行中的轮次");
        }
        if (isSettled(round)) {
            throw new BusinessException("本轮已结束，等待下周开局");
        }
        return round;
    }

    private boolean isSettled(GameRound round) {
        return round != null && round.getSettleAt() != null
                && LocalDateTime.now().isAfter(round.getSettleAt());
    }

    private GameRound runningRound() {
        return roundRepository.selectOne(new LambdaQueryWrapper<GameRound>()
                .eq(GameRound::getStatus, GameRound.STATUS_RUNNING)
                .orderByDesc(GameRound::getId)
                .last("LIMIT 1"));
    }

    private GameRound latestRound() {
        return roundRepository.selectOne(new LambdaQueryWrapper<GameRound>()
                .orderByDesc(GameRound::getId)
                .last("LIMIT 1"));
    }

    private GamePlayer findPlayer(Long roundId, Long userId) {
        return playerRepository.selectOne(new LambdaQueryWrapper<GamePlayer>()
                .eq(GamePlayer::getRoundId, roundId)
                .eq(GamePlayer::getUserId, userId));
    }

    private GamePlayer requirePlayer(Long roundId, Long userId) {
        GamePlayer player = findPlayer(roundId, userId);
        if (player == null) {
            throw new BusinessException("请先选择底盘加入本轮");
        }
        return player;
    }

    private GameCityOwner findOwner(Long roundId, Long cityId) {
        return ownerRepository.selectOne(new LambdaQueryWrapper<GameCityOwner>()
                .eq(GameCityOwner::getRoundId, roundId)
                .eq(GameCityOwner::getCityId, cityId));
    }

    private Map<Long, String> loadUsernames(List<GamePlayer> players) {
        List<Long> ids = players.stream().map(GamePlayer::getUserId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (User u : userRepository.selectBatchIds(ids)) {
            map.put(u.getId(), u.getUsername());
        }
        return map;
    }

    private int totalBoardPoints(int rank) {
        if (rank == 1) return 600;
        if (rank == 2) return 400;
        if (rank == 3) return 300;
        if (rank <= 5) return 200;
        if (rank <= 10) return 120;
        if (rank <= 20) return 60;
        if (rank <= 50) return 30;
        return 0;
    }

    private int rookieBoardPoints(int rank) {
        if (rank == 1) return 200;
        if (rank == 2) return 120;
        if (rank == 3) return 80;
        return 0;
    }

    private LocalDateTime nextOccurrence(DayOfWeek day, LocalTime time) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime candidate = now.toLocalDate().atTime(time)
                .with(TemporalAdjusters.nextOrSame(day));
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate;
    }
}
