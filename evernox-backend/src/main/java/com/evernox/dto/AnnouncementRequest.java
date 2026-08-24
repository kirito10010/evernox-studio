package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公告创建/更新请求
 *
 * content 是富文本 HTML，入库前由 NoteHtmlSanitizer 按白名单消毒，因此这里只限制体积。
 */
@Data
public class AnnouncementRequest {

    /** 标签ID(announcement_tag.id, 可空) */
    private Long tagId;

    /** 标题 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题不能超过100字")
    private String title;

    /** 正文 HTML（插图以 <img data-image-id="..."> 形式出现） */
    @Size(max = 200_000, message = "正文过长")
    private String content;
}
