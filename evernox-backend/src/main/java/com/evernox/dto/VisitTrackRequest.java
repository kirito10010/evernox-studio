package com.evernox.dto;

import lombok.Data;

/**
 * 访问上报请求
 */
@Data
public class VisitTrackRequest {

    /** 当前路由 path */
    private String path;
}
