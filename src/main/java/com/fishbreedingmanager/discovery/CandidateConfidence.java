package com.fishbreedingmanager.discovery;

/** 鱼类候选的自动识别置信度，不表示玩法规则已经启用。 */
public enum CandidateConfidence {
    /** 至少命中一个强信号。 */
    HIGH,
    /** 命中至少两个独立弱信号语义组。 */
    MEDIUM,
    /** 只有一个弱信号语义组或仅存在于完整 Registry。 */
    LOW
}
