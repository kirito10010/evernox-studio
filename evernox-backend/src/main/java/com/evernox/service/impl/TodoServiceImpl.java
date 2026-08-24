package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.dto.TodoRequest;
import com.evernox.dto.TodoResponse;
import com.evernox.dto.TodoStatsResponse;
import com.evernox.entity.Todo;
import com.evernox.exception.BusinessException;
import com.evernox.repository.TodoRepository;
import com.evernox.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 待办事项服务实现
 *
 * 「逾期」「今天」一律以服务端当天日期为准：客户端时钟不可信。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TodoServiceImpl implements TodoService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PRIORITY = 1;

    private final TodoRepository todoRepository;

    @Override
    @Transactional
    public TodoResponse create(TodoRequest request, Long userId) {
        Todo todo = Todo.builder()
                .userId(userId)
                .content(request.getContent().trim())
                .done(0)
                .priority(request.getPriority() != null ? request.getPriority() : DEFAULT_PRIORITY)
                .dueDate(request.getDueDate())
                .deleted(0)
                .build();
        todoRepository.insert(todo);
        return TodoResponse.from(todo, LocalDate.now());
    }

    @Override
    @Transactional
    public TodoResponse update(Long id, TodoRequest request, Long userId) {
        Todo todo = requireOwned(id, userId);
        todo.setContent(request.getContent().trim());
        todo.setPriority(request.getPriority() != null ? request.getPriority() : DEFAULT_PRIORITY);
        // 截止日期允许被清空，updateById 会忽略 null，因此这里直接置字段后走全量更新
        todo.setDueDate(request.getDueDate());
        todoRepository.updateById(todo);
        if (request.getDueDate() == null) {
            clearDueDate(id, userId);
            todo.setDueDate(null);
        }
        return TodoResponse.from(todo, LocalDate.now());
    }

    @Override
    @Transactional
    public TodoResponse setDone(Long id, boolean done, Long userId) {
        Todo todo = requireOwned(id, userId);
        todo.setDone(done ? 1 : 0);
        todo.setFinishedAt(done ? LocalDateTime.now() : null);
        todoRepository.updateById(todo);
        if (!done) {
            // updateById 忽略 null，取消完成必须显式把完成时间清掉
            clearFinishedAt(id, userId);
            todo.setFinishedAt(null);
        }
        return TodoResponse.from(todo, LocalDate.now());
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        requireOwned(id, userId);
        todoRepository.deleteById(id);
    }

    @Override
    public IPage<TodoResponse> list(Long userId, Integer done, Integer priority, String dueScope,
                                    int page, int size) {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<Todo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Todo::getUserId, userId)
                .eq(done != null, Todo::getDone, done)
                .eq(priority != null, Todo::getPriority, priority);

        if (dueScope != null) {
            switch (dueScope) {
                case "today" -> wrapper.eq(Todo::getDueDate, today);
                case "week" -> wrapper.between(Todo::getDueDate, today, today.plusDays(7));
                case "overdue" -> wrapper.lt(Todo::getDueDate, today).eq(Todo::getDone, 0);
                default -> {
                    // 未识别的取值当作不限，不额外报错
                }
            }
        }

        // 未完成优先 → 截止日期近的在前（无日期排最后）→ 优先级高的在前
        // 「无日期排最后」用 lambda 排序表达不了，整段排序统一写在 last 里（不能再叠 orderBy，否则会拼出两段 ORDER BY）
        wrapper.last("ORDER BY `done` ASC, (`due_date` IS NULL) ASC, `due_date` ASC, "
                + "`priority` DESC, `id` DESC");

        IPage<Todo> raw = todoRepository.selectPage(newPage(page, size), wrapper);
        Page<TodoResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(t -> TodoResponse.from(t, today)).toList());
        return result;
    }

    @Override
    public TodoStatsResponse getStats(Long userId) {
        LocalDate today = LocalDate.now();
        return TodoStatsResponse.builder()
                .pending(count(userId, w -> w.eq(Todo::getDone, 0)))
                .dueToday(count(userId, w -> w.eq(Todo::getDone, 0).eq(Todo::getDueDate, today)))
                .overdue(count(userId, w -> w.eq(Todo::getDone, 0).lt(Todo::getDueDate, today)))
                .done(count(userId, w -> w.eq(Todo::getDone, 1)))
                .build();
    }

    private long count(Long userId, java.util.function.Consumer<LambdaQueryWrapper<Todo>> extra) {
        LambdaQueryWrapper<Todo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Todo::getUserId, userId);
        extra.accept(wrapper);
        return todoRepository.selectCount(wrapper);
    }

    private void clearDueDate(Long id, Long userId) {
        todoRepository.update(null, new LambdaUpdateWrapper<Todo>()
                .eq(Todo::getId, id)
                .eq(Todo::getUserId, userId)
                .set(Todo::getDueDate, null));
    }

    private void clearFinishedAt(Long id, Long userId) {
        todoRepository.update(null, new LambdaUpdateWrapper<Todo>()
                .eq(Todo::getId, id)
                .eq(Todo::getUserId, userId)
                .set(Todo::getFinishedAt, null));
    }

    /** 取出并校验归属，越权直接 403 */
    private Todo requireOwned(Long id, Long userId) {
        Todo todo = todoRepository.selectById(id);
        if (todo == null) {
            throw new BusinessException("待办不存在");
        }
        if (!todo.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该待办");
        }
        return todo;
    }

    private Page<Todo> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
