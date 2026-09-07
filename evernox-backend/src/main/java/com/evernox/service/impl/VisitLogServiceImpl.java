package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.dto.VisitLogItemResponse;
import com.evernox.dto.VisitOverviewResponse;
import com.evernox.dto.VisitTrendItem;
import com.evernox.dto.VisitUserRankItem;
import com.evernox.entity.User;
import com.evernox.entity.UserVisitLog;
import com.evernox.repository.UserRepository;
import com.evernox.repository.UserVisitLogRepository;
import com.evernox.service.VisitLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台访问日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitLogServiceImpl implements VisitLogService {

    private static final String TYPE_LOGIN = "LOGIN";
    private static final String TYPE_VISIT = "VISIT";

    private final UserVisitLogRepository visitLogRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void recordLogin(Long userId, String ip, String userAgent) {
        record(userId, ip, userAgent, null, TYPE_LOGIN);
    }

    @Override
    public void recordVisit(Long userId, String ip, String userAgent, String path) {
        record(userId, ip, userAgent, path, TYPE_VISIT);
    }

    private void record(Long userId, String ip, String userAgent, String path, String type) {
        if (userId == null) {
            return;
        }
        try {
            User user = userRepository.selectById(userId);
            if (user == null || "admin".equals(user.getRole())) {
                return;
            }
            visitLogRepository.insert(UserVisitLog.builder()
                    .userId(userId)
                    .username(user.getUsername())
                    .type(type)
                    .ip(ip)
                    .userAgent(userAgent)
                    .path(path)
                    .build());
        } catch (Exception e) {
            // 记录失败不影响登录/访问主流程
            log.warn("记录访问日志失败: userId={}, type={}, {}", userId, type, e.getMessage());
        }
    }

    @Override
    public VisitOverviewResponse overview() {
        return VisitOverviewResponse.builder()
                .todayActiveUsers(count("SELECT COUNT(DISTINCT user_id) FROM user_visit_log WHERE created_at >= CURDATE()"))
                .todayVisits(count("SELECT COUNT(*) FROM user_visit_log WHERE type='VISIT' AND created_at >= CURDATE()"))
                .todayLogins(count("SELECT COUNT(*) FROM user_visit_log WHERE type='LOGIN' AND created_at >= CURDATE()"))
                .weekActiveUsers(count("SELECT COUNT(DISTINCT user_id) FROM user_visit_log WHERE created_at >= CURDATE() - INTERVAL 6 DAY"))
                .build();
    }

    @Override
    public List<VisitTrendItem> trend(int days) {
        int d = days <= 0 ? 7 : days;
        String sql = "SELECT DATE(created_at) AS d, COUNT(*) AS visits, COUNT(DISTINCT user_id) AS active_users, " +
                "SUM(CASE WHEN type='LOGIN' THEN 1 ELSE 0 END) AS logins " +
                "FROM user_visit_log WHERE created_at >= CURDATE() - INTERVAL ? DAY " +
                "GROUP BY DATE(created_at) ORDER BY d ASC";
        return jdbcTemplate.query(sql,
                (rs, i) -> VisitTrendItem.builder()
                        .date(rs.getDate("d").toLocalDate())
                        .visits(rs.getLong("visits"))
                        .activeUsers(rs.getLong("active_users"))
                        .logins(rs.getLong("logins"))
                        .build(),
                d - 1);
    }

    @Override
    public List<VisitUserRankItem> rank(int top) {
        int t = top <= 0 ? 10 : top;
        String sql = "SELECT user_id, username, COUNT(*) AS cnt, MAX(created_at) AS last_at " +
                "FROM user_visit_log GROUP BY user_id, username ORDER BY cnt DESC LIMIT ?";
        return jdbcTemplate.query(sql,
                (rs, i) -> VisitUserRankItem.builder()
                        .userId(rs.getLong("user_id"))
                        .username(rs.getString("username"))
                        .visitCount(rs.getLong("cnt"))
                        .lastVisitAt(rs.getTimestamp("last_at").toLocalDateTime())
                        .build(),
                t);
    }

    @Override
    public IPage<VisitLogItemResponse> recent(int page, int size, String type, String username) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (type != null && !type.isBlank()) {
            where.append("WHERE type = ?");
            args.add(type);
        }
        if (username != null && !username.isBlank()) {
            where.append(where.length() == 0 ? "WHERE " : " AND ");
            where.append("username LIKE ?");
            args.add("%" + username + "%");
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_visit_log " + where, Long.class, args.toArray());
        long t = total == null ? 0L : total;

        int p = Math.max(1, page);
        int s = size <= 0 ? 20 : size;
        String dataSql = "SELECT id, user_id, username, type, ip, user_agent, path, created_at " +
                "FROM user_visit_log " + where + " ORDER BY id DESC LIMIT ? OFFSET ?";
        List<Object> dataArgs = new ArrayList<>(args);
        dataArgs.add(s);
        dataArgs.add((long) (p - 1) * s);

        List<VisitLogItemResponse> records = jdbcTemplate.query(dataSql,
                (rs, i) -> VisitLogItemResponse.builder()
                        .id(rs.getLong("id"))
                        .userId(rs.getLong("user_id"))
                        .username(rs.getString("username"))
                        .type(rs.getString("type"))
                        .ip(rs.getString("ip"))
                        .userAgent(rs.getString("user_agent"))
                        .path(rs.getString("path"))
                        .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                        .build(),
                dataArgs.toArray());

        Page<VisitLogItemResponse> result = new Page<>(p, s);
        result.setRecords(records);
        result.setTotal(t);
        return result;
    }

    @Override
    @Scheduled(cron = "0 30 3 * * *")
    public void cleanup() {
        try {
            int deleted = jdbcTemplate.update(
                    "DELETE FROM user_visit_log WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY)");
            if (deleted > 0) {
                log.info("清理 90 天前访问日志 {} 条", deleted);
            }
        } catch (Exception e) {
            log.error("清理访问日志失败: {}", e.getMessage());
        }
    }

    private Long count(@NonNull String sql) {
        Long v = jdbcTemplate.queryForObject(sql, Long.class);
        return v == null ? 0L : v;
    }
}
