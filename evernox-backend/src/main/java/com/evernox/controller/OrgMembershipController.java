package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.OrgMembershipResponse;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.OrgMembershipService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织成员关系（申请/退出）接口
 */
@RestController
@RequestMapping("/org/membership")
@RequiredArgsConstructor
public class OrgMembershipController {

    private final OrgMembershipService membershipService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/mine")
    public Result<List<OrgMembershipResponse>> mine(HttpServletRequest http) {
        return Result.success(membershipService.listMine(getUserId(http)));
    }

    @PostMapping("/apply")
    public Result<Void> apply(@RequestParam Long organizationId, HttpServletRequest http) {
        membershipService.apply(getUserId(http), organizationId);
        return Result.success("申请已提交，请等待审批", null);
    }

    @PostMapping("/leave")
    public Result<Void> leave(@RequestParam Long organizationId, HttpServletRequest http) {
        membershipService.leave(getUserId(http), organizationId);
        return Result.success("已退出组织", null);
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
