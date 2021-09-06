package org.dromara.hodor.server.executor;

import org.dromara.hodor.common.dag.Dag;
import org.dromara.hodor.core.dag.DagCreator;
import org.dromara.hodor.common.dag.Status;
import org.dromara.hodor.common.storage.cache.CacheSource;
import org.dromara.hodor.common.storage.cache.HodorCacheSource;
import org.dromara.hodor.model.job.JobKey;
import org.dromara.hodor.scheduler.api.HodorJobExecutionContext;
import org.dromara.hodor.server.ServiceProvider;

/**
 *  workflow job executor
 *
 * @author tomgs
 * @version 2020/6/25 1.0 
 */
public class FlowJobExecutor extends CommonJobExecutor {

    private final CacheSource<JobKey, Dag> cacheSource;

    public FlowJobExecutor() {
        HodorCacheSource hodorCacheSource = ServiceProvider.getInstance().getBean(HodorCacheSource.class);
        this.cacheSource = hodorCacheSource.getCacheSource("flow_job_executor");
    }

    @Override
    public void process(HodorJobExecutionContext context) {
        // 1、判断是否有正在运行的flow
        // 2、没有则获取flow json信息
        // 3、构建dag对象
        // 4、存储dag对象详情
        // 5、提交运行dag
        if (isAlreadyRunningJob(context.getJobKey())) {
            return;
        }
        Dag dagInstance = createDagInstance(context);
        if (isAlreadyRunningJob(context.getJobKey())) {
            return;
        }
        submitDagInstance(dagInstance);
    }

    private Dag createDagInstance(HodorJobExecutionContext context) {
        DagCreator dagCreator = new DagCreator(null);
        return dagCreator.create();
    }

    private boolean isAlreadyRunningJob(JobKey jobKey) {
        Dag dag = cacheSource.get(jobKey);
        if (dag == null) {
            return false;
        }
        return !dag.getStatus().isTerminal();
    }

    private void submitDagInstance(Dag dag) {
        dag.setStatus(Status.RUNNING);
        dag.getFirstLayer().ifPresent(FlowJobExecutorManager.getInstance()::submitLayerNode);
    }

}