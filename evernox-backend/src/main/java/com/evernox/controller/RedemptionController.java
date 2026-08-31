package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.RedemptionCodeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 卡密兑换控制器
 */
@RestController
@RequestMapping("/redemption")
@RequiredArgsConstructor
public class RedemptionController {

    private final RedemptionCodeService redemptionCodeService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/redeem")
    public Result<Void> redeem(@RequestBody RedeemRequest request, HttpServletRequest http) {
        redemptionCodeService.redeem(getUserId(http), request.getCode());
        return Result.success("兑换成功", null);
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }

    @Data
    public static class RedeemRequest {
        private String code;
    }
}
