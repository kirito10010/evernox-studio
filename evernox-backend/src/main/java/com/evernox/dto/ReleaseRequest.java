package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新建/编辑版本发布
 */
@Data
public class ReleaseRequest {

    /** 新建时必填，编辑时忽略 */
    private Long projectId;

    @NotBlank(message = "版本标签不能为空")
    @Size(max = 60, message = "版本标签不能超过 60 个字符")
    @Pattern(regexp = "^[vV]?\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.-]+)?$",
            message = "版本标签需符合 v1.2.3 或 1.2.3-beta.1")
    private String tag;

    @NotBlank(message = "发布标题不能为空")
    @Size(max = 200, message = "发布标题不能超过 200 个字符")
    private String title;

    private String notes;

    /** 该版本最终保留的附件集合 */
    private List<Long> assetIds;
}
