package org.dromara.hodor.server.component;

/**
 * event type
 *
 * @author tomgs
 */
public interface EventType {

    /**
     * 任务初始化分发
     */
    String JOB_INIT_DISTRIBUTE = "jobInitDistribute";

    /**
     * 任务创建事件
     */
    String JOB_CREATE_DISTRIBUTE = "jobUpdateDistribute";

    /**
     * 任务更新事件
     */
    String JOB_UPDATE_DISTRIBUTE = "jobUpdateDistribute";

}
