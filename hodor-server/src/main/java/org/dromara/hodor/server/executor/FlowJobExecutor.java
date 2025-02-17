package org.dromara.hodor.server.executor;

import lombok.extern.slf4j.Slf4j;
import org.dromara.hodor.common.dag.Dag;
import org.dromara.hodor.common.dag.Status;
import org.dromara.hodor.common.utils.Utils.Assert;
import org.dromara.hodor.core.dag.DagCreator;
import org.dromara.hodor.core.dag.FlowData;
import org.dromara.hodor.model.job.JobKey;
import org.dromara.hodor.scheduler.api.HodorJobExecutionContext;

/**
 *  workflow job executor
 *
 * @author tomgs
 * @version 2020/6/25 1.0 
 */
@Slf4j
public class FlowJobExecutor extends CommonJobExecutor {

    private final FlowJobExecutorManager flowJobExecutorManager;

    public FlowJobExecutor() {
        this.flowJobExecutorManager = FlowJobExecutorManager.getInstance();
    }

    @Override
    public void process(HodorJobExecutionContext context) {
        // 1、判断是否有正在运行的flow
        // 2、没有则获取flow json信息
        // 3、构建dag对象
        // 4、存储dag对象详情
        // 5、提交运行dag
        // TODO: add to timeout checking
        if (isAlreadyRunningJob(context.getJobKey())) {
            log.error("dag {} is already running.", context.getJobKey());
            return;
        }
        Dag dagInstance = createDagInstance(context);
        addRunningDag(context.getJobKey(), dagInstance);
        submitDagInstance(dagInstance);
    }

    private void addRunningDag(JobKey jobKey, Dag dagInstance) {
        dagInstance.setStatus(Status.RUNNING);
        flowJobExecutorManager.createDagInstance(jobKey, dagInstance);
    }

    private Dag createDagInstance(HodorJobExecutionContext context) {
        FlowData flowData = flowJobExecutorManager.getFlowData(context.getJobKey());
        Assert.notNull(flowData, "not found flow node by job key {}.", context.getJobKey());
        DagCreator dagCreator = new DagCreator(flowData);
        Dag dag = dagCreator.create();
        dag.setSchedulerName(context.getSchedulerName());
        return dag;
    }

    private boolean isAlreadyRunningJob(JobKey jobKey) {
        Dag dag = flowJobExecutorManager.getDagInstance(jobKey);
        if (dag == null) {
            return false;
        }
        if (dag.getStatus().isTerminal()) {
            return false;
        }
        // check dag status
        dag.updateDagStatus();
        if (dag.getStatus().isTerminal()) {
            flowJobExecutorManager.updateDagStatus(dag);
            return false;
        }
        return true;
    }

    private void submitDagInstance(Dag dag) {
        flowJobExecutorManager.startDag(dag);
    }

}