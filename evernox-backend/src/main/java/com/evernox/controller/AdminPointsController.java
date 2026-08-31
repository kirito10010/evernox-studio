package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.PointsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员积分与会员管理控制器
 */
@RestController
@RequestMapping("/admin/points")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminPointsController {

    private final PointsService pointsService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/recharge")
    public Result<Void> recharge(@RequestBody RechargeRequest request, HttpServletRequest http) {
        pointsService.recharge(getUserId(http), request.getUserId(), request.getPoints(), request.getDescription());
        return Result.success("充值成功", null);
    }

    @PostMapping("/set-super-member")
    public Result<Void> setSuperMember(@RequestBody SetSuperMemberRequest request) {
        pointsService.setSuperMember(request.getUserId(), request.getDays());
        return Result.success("设置成功", null);
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }

    @Data
    public static class RechargeRequest {
        private Long userId;
        private Integer points;
        private String description;
    }

    @Data
    public static class SetSuperMemberRequest {
        private Long userId;
        private Integer days;
    }
}
