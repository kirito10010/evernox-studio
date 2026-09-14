package com.evernox.service;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * AI 资讯标题/摘要翻译（百度翻译开放平台 · 通用文本翻译 API）。
 *
 * 未配置 appid/key 时自动跳过，不影响采集主流程。
 */
@Slf4j
@Component
public class AiNewsTranslator {

    private static final String URL = "https://fanyi-api.baidu.com/api/trans/vip/translate";

    @Value("${evernox.ai-news.translate.appid:}")
    private String appid;

    @Value("${evernox.ai-news.translate.key:}")
    private String secretKey;

    public boolean enabled() {
        return appid != null && !appid.isBlank() && secretKey != null && !secretKey.isBlank();
    }

    /** 翻译成中文；失败返回 null，调用方回退原文 */
    public String translateToZh(String text) {
        if (!enabled() || text == null || text.isBlank()) {
            return null;
        }
        String q = text.length() > 1800 ? text.substring(0, 1800) : text;
        String salt = String.valueOf(System.currentTimeMillis());
        String sign = SecureUtil.md5(appid + q + salt + secretKey);
        try {
            String formBody = "q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                    + "&from=auto&to=zh&appid=" + appid
                    + "&salt=" + salt + "&sign=" + sign;
            String body = HttpRequest.post(URL)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(formBody)
                    .timeout(8000)
                    .execute()
                    .body();
            JSONObject json = JSONUtil.parseObj(body);
            if (json.containsKey("error_code")) {
                log.warn("翻译失败: {} {}", json.getStr("error_code"), json.getStr("error_msg"));
                return null;
            }
            JSONArray arr = json.getJSONArray("trans_result");
            if (arr == null || arr.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                String dst = arr.getJSONObject(i).getStr("dst");
                if (dst != null) {
                    sb.append(dst);
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        } catch (Exception e) {
            log.warn("翻译请求异常: {}", e.getMessage());
            return null;
        }
    }
}
