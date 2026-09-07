package com.evernox.dto;

import com.evernox.entity.SupportBoard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportBoardResponse {

    private Long id;
    private String name;
    private Integer width;
    private Integer height;
    private Integer active;
    private LocalDateTime createdAt;

    public static SupportBoardResponse from(SupportBoard b) {
        return SupportBoardResponse.builder()
                .id(b.getId())
                .name(b.getName())
                .width(b.getWidth())
                .height(b.getHeight())
                .active(b.getActive())
                .createdAt(b.getCreatedAt())
                .build();
    }
}
