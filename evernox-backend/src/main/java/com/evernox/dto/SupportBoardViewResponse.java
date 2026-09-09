package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportBoardViewResponse {

    private Long id;
    private String name;
    private Integer width;
    private Integer height;
    private Integer active;
    /** 扁平数组：[x, y, colorInt, locked, ...] */
    private List<Integer> pixels;
}
