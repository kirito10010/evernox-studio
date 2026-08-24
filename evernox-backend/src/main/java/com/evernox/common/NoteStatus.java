package com.evernox.common;

/**
 * 记事本状态常量
 *
 * 语义与 SiteStatus 一致（另立一个类是为了让笔记的状态流转独立演进）：
 * 0 私有 --submit--> 1 待审批 --approve--> 2 已公开
 * 1 待审批 --reject--> 3 已驳回 --submit--> 1
 * 1/2 --withdraw/offline--> 0
 */
public final class NoteStatus {

    /** 私有，仅作者可见 */
    public static final int PRIVATE = 0;

    /** 待审批 */
    public static final int PENDING = 1;

    /** 已公开 */
    public static final int PUBLIC = 2;

    /** 已驳回，带驳回原因 */
    public static final int REJECTED = 3;

    private NoteStatus() {
    }
}
