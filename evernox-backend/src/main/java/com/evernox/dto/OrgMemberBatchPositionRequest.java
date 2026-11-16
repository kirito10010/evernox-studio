package com.evernox.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 组织成员批量设置职务请求；position 为 null 或空串表示清空职务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgMemberBatchPositionRequest {

    @NotEmpty(message = "请先选择成员")
    private List<Long> ids;

    @Size(max = 50, message = "职务长度不能超过50字符")
    private String position;
}
