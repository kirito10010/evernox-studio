package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建/编辑发布项目
 */
@Data
public class ReleaseProjectRequest {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 80, message = "项目名称不能超过 80 个字符")
    private String name;

    @NotBlank(message = "slug 不能为空")
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,39}$",
            message = "slug 只能使用小写字母、数字和短横线，2-40 个字符")
    private String slug;


    @Size(max = 500, message = "项目描述不能超过 500 个字符")
    private String description;

    private Integer sort;
}
