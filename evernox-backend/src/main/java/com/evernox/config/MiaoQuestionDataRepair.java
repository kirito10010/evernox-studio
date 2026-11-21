package com.evernox.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evernox.entity.MiaoQuestion;
import com.evernox.repository.MiaoQuestionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 喵问答历史脏数据修复（启动时执行，幂等）。
 *
 * <p>旧版导入只按「题目文本」匹配，看图题文案相同就会互相覆盖，
 * 导致排除项里出现不属于当前四个选项的值；旧逻辑还会用 3 个排除项反推答案。
 *
 * <p>这里只做“减法”，绝不反推答案：
 *  - 排除项里不属于当前四个选项的值 → 删除；
 *  - 排除项被清理过（说明遭过串题污染）→ 答案一并清空；
 *  - 答案不在四个选项内、或答案落在排除项里 → 清空，等待用户重新投票确认。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiaoQuestionDataRepair implements ApplicationRunner {

    private static final int MAX_REMOVED = 3;

    private final MiaoQuestionRepository questionRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        List<MiaoQuestion> rows = questionRepository.selectList(new QueryWrapper<MiaoQuestion>());
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int removedFixed = 0;
        int answerFixed = 0;
        for (MiaoQuestion row : rows) {
            List<String> options = optionsOf(row);
            List<String> removed = parseRemoved(row.getRemovedOptions());
            List<String> validRemoved = sanitize(options, removed);
            // 排除项被清理过 → 说明这道题确实被串题数据污染过，答案也不可信
            boolean contaminated = !validRemoved.equals(removed);

            boolean changed = false;
            if (contaminated) {
                row.setRemovedOptions(writeRemoved(validRemoved));
                removedFixed++;
                changed = true;
            }
            String answer = row.getAnswer() == null ? "" : row.getAnswer().trim();
            if (!answer.isEmpty()
                    && (contaminated || !options.contains(answer) || validRemoved.contains(answer))) {
                row.setAnswer("");
                answerFixed++;
                changed = true;
            }
            if (changed) {
                questionRepository.updateById(row);
            }
        }
        if (removedFixed > 0 || answerFixed > 0) {
            log.warn("喵问答历史数据修复完成：清理非法排除项 {} 条，清空无效答案 {} 条",
                    removedFixed, answerFixed);
        }
    }

    private static List<String> optionsOf(MiaoQuestion row) {
        return List.of(
                trim(row.getOptionA()),
                trim(row.getOptionB()),
                trim(row.getOptionC()),
                trim(row.getOptionD()));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** 只保留属于选项的值、去重、最多 3 个 */
    private static List<String> sanitize(List<String> options, List<String> removed) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (removed != null) {
            for (String value : removed) {
                if (result.size() >= MAX_REMOVED) {
                    break;
                }
                if (value != null && !value.isBlank() && options.contains(value.trim())) {
                    result.add(value.trim());
                }
            }
        }
        return new ArrayList<>(result);
    }

    private List<String> parseRemoved(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> parsed = objectMapper.readValue(raw, new TypeReference<List<String>>() {
            });
            return parsed == null ? new ArrayList<>() : parsed;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String writeRemoved(List<String> removed) {
        try {
            return objectMapper.writeValueAsString(removed);
        } catch (Exception e) {
            return "[]";
        }
    }
}
