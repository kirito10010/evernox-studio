package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.RedemptionCodeResponse;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.RedemptionCodeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员卡密管理控制器
 */
@RestController
@RequestMapping("/admin/redemption")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminRedemptionController {

    private final RedemptionCodeService redemptionCodeService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/generate")
    public Result<List<RedemptionCodeResponse>> generate(@RequestBody GenerateRequest request,
                                                         HttpServletRequest http) {
        return Result.success("生成成功", redemptionCodeService.generate(
                getUserId(http), request.getDays(), request.getCount()));
    }

    @GetMapping("/list")
    public Result<List<RedemptionCodeResponse>> list() {
        return Result.success(redemptionCodeService.list());
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }

    @Data
    public static class GenerateRequest {
        private Integer days;
        private Integer count;
    }
}
