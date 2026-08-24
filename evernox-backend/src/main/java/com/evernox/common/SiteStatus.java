package com.evernox.common;

/**
 * 网站分享状态常量
 *
 * 公开与否只看这一个字段，状态流转：
 * 0 私有 --submit--> 1 待审批 --approve--> 2 已公开
 * 1 待审批 --reject--> 3 已驳回 --submit--> 1
 * 1/2 --withdraw/offline--> 0
 */
public final class SiteStatus {

    /** 私有，仅所有者可见 */
    public static final int PRIVATE = 0;

    /** 待审批 */
    public static final int PENDING = 1;

    /** 已公开 */
    public static final int PUBLIC = 2;

    /** 已驳回，带驳回原因 */
    public static final int REJECTED = 3;

    private SiteStatus() {
    }
}
