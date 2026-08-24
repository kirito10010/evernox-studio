package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.AdminUserCreateRequest;
import com.evernox.dto.AdminUserUpdateRequest;
import com.evernox.dto.UserInfoResponse;
import com.evernox.dto.UserOptionResponse;
import com.evernox.dto.UserStatsResponse;

import java.util.List;

/**
 * 管理员用户管理服务
 *
 * 所有方法都排除 role=admin 的账号：列表查询强制过滤，按 ID 的操作先校验目标角色。
 * 仅靠列表过滤挡不住直接构造 ID 的越权请求。
 */
public interface AdminUserService {

    /**
     * 分页查询用户（不含管理员）
     *
     * @param page       页码，从 1 开始
     * @param size       每页条数
     * @param keyword    用户名或邮箱模糊匹配，可空
     * @param role       角色过滤，可空
     * @param status     状态过滤，可空
     * @param startDate  注册时间下界 yyyy-MM-dd，可空
     * @param endDate    注册时间上界 yyyy-MM-dd（含当天），可空
     * @param sortField  排序字段，白名单外回退为 createdAt
     * @param sortOrder  asc / desc，默认 desc
     */
    IPage<UserInfoResponse> listUsers(int page, int size, String keyword, String role, Integer status,
                                      String startDate, String endDate, String sortField, String sortOrder);

    /** 用户统计（不含管理员） */
    UserStatsResponse getStats();

    /**
     * 筛选下拉用的全部用户清单（含管理员本人，资产归属可能是管理员自己）
     */
    List<UserOptionResponse> listUserOptions();


    /** 获取单个用户详情 */
    UserInfoResponse getUser(Long id);

    /** 创建用户 */
    UserInfoResponse createUser(AdminUserCreateRequest request);

    /** 更新用户 */
    UserInfoResponse updateUser(Long id, AdminUserUpdateRequest request);

    /** 更新状态：1启用 / 0禁用 */
    UserInfoResponse updateStatus(Long id, Integer status);

    /** 重置密码 */
    void resetPassword(Long id, String password);

    /** 删除用户（级联清理其图片、相册与磁盘文件，不可恢复） */
    void deleteUser(Long id);

    /** 批量删除；若其中包含管理员账号则整批拒绝 */
    void deleteUsers(List<Long> ids);
}
