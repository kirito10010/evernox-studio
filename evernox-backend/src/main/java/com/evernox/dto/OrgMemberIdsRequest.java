package com.evernox.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 组织成员批量操作请求：成员ID集合
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgMemberIdsRequest {

    @NotEmpty(message = "请先选择成员")
    private List<Long> ids;
}
