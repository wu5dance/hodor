package org.dromara.hodor.actuator.bigdata.jobtype.asyncHadoop;

import java.util.Set;
import org.apache.logging.log4j.Logger;
import org.dromara.hodor.actuator.api.exceptions.JobExecutionException;
import org.dromara.hodor.actuator.api.utils.Props;
import org.dromara.hodor.actuator.bigdata.core.queue.AsyncTaskStateChecker;
import org.dromara.hodor.actuator.bigdata.jobtype.HadoopJavaJob;
import org.dromara.hodor.actuator.bigdata.jobtype.HadoopJobUtils;
import org.dromara.hodor.actuator.bigdata.jobtype.javautils.AsyncJobStateTask;

import static org.dromara.hodor.actuator.bigdata.core.executor.Constants.JobProperties.JOB_LOG_PATH;


/**
 * 异步提交hadoop任务
 *
 * @author tomgs
 * @since  1.0
 **/
public class AsyncHadoopJavaJob extends HadoopJavaJob {

    private final Logger logger;

    private final Props props;

    private final String jobid;

    public AsyncHadoopJavaJob(String jobid, Props sysProps, Props jobProps, Logger logger) throws RuntimeException {
        super(jobid, sysProps, jobProps, logger);
        getJobProps().put("execute.as.async", "true");
        this.logger = logger;
        this.props = jobProps;
        this.jobid = jobid;
    }

    @Override
    public void run() throws Exception {
        super.run();
        String logPath = jobProps.getString(JOB_LOG_PATH);
        Set<String> applicationIds = HadoopJobUtils.findApplicationIdFromLog(logPath, logger);
        logger.info("hadoop submit applicationIds:" + applicationIds);
        if (applicationIds.size() <= 0) {
            throw new JobExecutionException("applicationId is null, failed to run job " + jobid);
        }
        //这里只处理一个任务的情况
        String applicationId = applicationIds.iterator().next();
        Long requestId = jobProps.getLong("requestId");
        int queueSize = getQueueSize(applicationId, requestId);
        logger.info("current queue size : " + queueSize);
    }

    private int getQueueSize(String applicationId, Long requestId) {
        AsyncJobStateTask task = new AsyncJobStateTask();
        task.setAppId(applicationId);
        task.setRequestId(requestId);
        task.setProps(props);
        AsyncTaskStateChecker stateCheckHandler = AsyncTaskStateChecker.getInstance();
        return stateCheckHandler.addTask(task);
    }

}
