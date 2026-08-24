package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.TodoRequest;
import com.evernox.dto.TodoResponse;
import com.evernox.dto.TodoStatsResponse;

/**
 * 待办事项服务（纯私有数据，所有方法都按 userId 隔离）
 */
public interface TodoService {

    TodoResponse create(TodoRequest request, Long userId);

    TodoResponse update(Long id, TodoRequest request, Long userId);

    /** 切换完成态；done=true 时记录完成时间，取消完成时清空 */
    TodoResponse setDone(Long id, boolean done, Long userId);

    void delete(Long id, Long userId);

    /**
     * @param done     null 全部 / 0 未完成 / 1 已完成
     * @param priority null 不限
     * @param dueScope null 不限 / today / week / overdue
     */
    IPage<TodoResponse> list(Long userId, Integer done, Integer priority, String dueScope,
                             int page, int size);

    TodoStatsResponse getStats(Long userId);
}
