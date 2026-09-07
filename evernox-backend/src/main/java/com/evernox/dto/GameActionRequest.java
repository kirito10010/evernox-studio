package com.evernox.dto;

import lombok.Data;

/**
 * 沙盘争霸操作请求
 */
@Data
public class GameActionRequest {

    /** occupy(占领无主)/attack(进攻有主)/garrison(驻防)/develop(建设) */
    private String type;

    private Long cityId;

    /** attack 投入的兵力 / garrison 驻防的兵力 */
    private Integer force;
}
