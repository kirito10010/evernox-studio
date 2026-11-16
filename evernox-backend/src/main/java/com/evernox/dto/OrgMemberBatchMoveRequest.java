package com.evernox.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 组织成员批量移入组织请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgMemberBatchMoveRequest {

    @NotEmpty(message = "请先选择成员")
    private List<Long> ids;

    @NotNull(message = "请选择目标组织")
    private Long organizationId;
}
