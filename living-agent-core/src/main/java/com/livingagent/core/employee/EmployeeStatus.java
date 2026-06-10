package com.livingagent.core.employee;

/**
 * 员工状态枚举 - 双维度设计：
 *
 * <p>1. 工作动作状态（决定前端区域分配）：
 *    - ACTIVE: 在线待命（默认状态）→ 工位区
 *    - WORKING: 正在执行任务 → 工位区
 *    - IDLE: 空闲休息中（无任务分配）→ 休息区 ✅
 *    - BUSY: 协作/会议中 → 协作区
 *
 * <p>2. 连接/生命周期状态（不影响区域，仅影响可用性显示）：
 *    - OFFLINE/DORMANT: 离线/休眠 → 离线区
 *    - DISABLED/ARCHIVED/TERMINATED: 不可用状态
 *
 * <p>3. 数字员工特殊状态：
 *    - LEARNING/EVOLVING: 学习/进化中 → 工位区
 */
public enum EmployeeStatus {
    // ========== 工作动作状态（决定区域分配） ==========
    /** 在线待命（默认状态），显示在工位区 */
    ACTIVE("在线/待命"),
    /** 正在执行任务，显示在工位区 */
    WORKING("工作中"),
    /** 空闲休息中（无任务），显示在休息区 */
    IDLE("空闲/休息中"),
    /** 协作/会议/处理复杂任务，显示在协作区 */
    BUSY("协作/忙渌中"),

    // ========== 连接/生命周期状态 ==========
    /** 离线，显示在离线区 */
    OFFLINE("离线"),
    /** 休眠待唤醒，显示在离线区 */
    DORMANT("休眠/待唤醒"),
    /** 已禁用，显示在告警区 */
    DISABLED("禁用"),
    /** 已归档，显示在离线区 */
    ARCHIVED("归档"),
    /** 已离职/销毁 */
    TERMINATED("离职/已销毁"),

    // ========== 数字员工特殊状态 ==========
    /** 学习中，显示在工位区 */
    LEARNING("学习中"),
    /** 进化中，显示在工位区 */
    EVOLVING("进化中");

    private final String description;

    EmployeeStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否为工作动作状态（非离线/不可用状态）
     */
    public boolean isWorkingStatus() {
        return this == WORKING || this == IDLE || this == BUSY || this == ACTIVE ||
               this == LEARNING || this == EVOLVING;
    }

    /**
     * 判断是否为生命周期状态（不影响工作区域）
     */
    public boolean isLifecycleStatus() {
        return this == DORMANT || this == DISABLED || this == ARCHIVED || this == TERMINATED;
    }

    /**
     * 判断是否为数字员工专属状态
     */
    public boolean isDigitalEmployeeOnly() {
        return this == LEARNING || this == EVOLVING;
    }

    /**
     * 判断是否为在线状态（可用于绿点显示）
     */
    public boolean isOnline() {
        return this != OFFLINE && this != DORMANT && this != DISABLED &&
               this != ARCHIVED && this != TERMINATED;
    }

    /**
     * 获取该状态应该显示的区域ID（用于前端区域分配）
     */
    public String getZoneId() {
        return switch (this) {
            case WORKING, ACTIVE, LEARNING, EVOLVING -> "workstation";
            case IDLE -> "lounge";  // ✅ 空闲员工去休息区
            case BUSY -> "collaboration";
            case OFFLINE, DORMANT, ARCHIVED -> "offline";
            case DISABLED -> "alert";
            case TERMINATED -> "offline";
        };
    }

    /**
     * 状态转换规则（状态机）
     */
    public boolean canTransitionTo(EmployeeStatus target) {
        return switch (this) {
            // 在线状态可以互相转换
            case ACTIVE ->
                target == WORKING || target == IDLE || target == BUSY ||
                target == OFFLINE || target == DORMANT;
            case WORKING ->
                target == ACTIVE || target == IDLE || target == BUSY ||
                target == OFFLINE || target == DORMANT;
            case IDLE ->
                target == ACTIVE || target == WORKING || target == BUSY ||
                target == OFFLINE || target == DORMANT;
            case BUSY ->
                target == ACTIVE || target == WORKING || target == IDLE ||
                target == OFFLINE || target == DORMANT;

            // 离线状态只能回到在线状态
            case OFFLINE -> target == ACTIVE || target == DORMANT;
            case DORMANT -> target == ACTIVE || target == TERMINATED || target == ARCHIVED;

            // 生命周期状态
            case DISABLED -> target == ACTIVE || target == TERMINATED || target == ARCHIVED;
            case ARCHIVED -> target == ACTIVE;
            case TERMINATED -> false;

            // 特殊状态
            case LEARNING -> target == EVOLVING || target == ACTIVE || target == WORKING;
            case EVOLVING -> target == ACTIVE || target == WORKING;
        };
    }
}
