package com.evernox.dto;

import lombok.Data;

/**
 * 发布/取消发布、设为/取消最新
 */
@Data
public class ReleaseLatestRequest {

    private Boolean latest;
}
