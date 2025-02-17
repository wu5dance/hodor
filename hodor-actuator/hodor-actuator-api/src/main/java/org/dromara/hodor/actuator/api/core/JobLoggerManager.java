package org.dromara.hodor.actuator.api.core;

import java.io.File;
import java.nio.file.Path;
import org.apache.logging.log4j.Logger;
import org.dromara.hodor.actuator.api.utils.JobPathUtils;
import org.dromara.hodor.common.log.LogUtil;
import org.dromara.hodor.common.utils.StringUtils;
import org.dromara.hodor.common.utils.Utils.Assert;

/**
 * job logger manager
 *
 * @author tomgs
 * @since 1.0
 */
public class JobLoggerManager {

    private static final JobLoggerManager INSTANCE = new JobLoggerManager();

    private JobLoggerManager() {
    }

    public static JobLoggerManager getInstance() {
        return INSTANCE;
    }

    public String getJobLoggerDir(String rootJobLogPath) {
        Assert.notBlank(rootJobLogPath, "rootJobLogPath must be not null.");
        return rootJobLogPath;
    }

    public String createLoggerName(String groupName, String jobName, Long requestId) {
        return StringUtils.format("{}_{}_{}_{}", System.currentTimeMillis(),
            groupName,
            jobName,
            requestId);
    }

    public String createLogFileName(String groupName, String jobName, Long requestId) {
        return StringUtils.format("_job.{}.{}.{}.log",
            groupName,
            jobName,
            requestId);
    }

    private File buildJobLoggerFile(String rootJobLogPath, String logFileName) {
        return new File(getJobLoggerDir(rootJobLogPath), logFileName);
    }

    public File buildJobLoggerFile(String rootJobLogPath, String groupName, String jobName, Long requestId) {
        Path executions = JobPathUtils.getExecutionsPath(rootJobLogPath, requestId);
        return buildJobLoggerFile(executions.toString(), createLogFileName(groupName, jobName, requestId));
    }

    public JobLogger createJobLogger(String rootJobLogPath, String groupName, String jobName, Long requestId) {
        File jobLoggerFile = buildJobLoggerFile(rootJobLogPath, groupName, jobName, requestId);
        return createJobLogger(createLoggerName(groupName, jobName, requestId), jobLoggerFile);
    }

    public JobLogger createJobLogger(String loggerName, File logFile) {
        Logger logger = LogUtil.getInstance().createLogger(loggerName, logFile);
        return new JobLogger(loggerName, logFile.toPath(), logger);
    }

    public void stopJobLogger(String loggerName) {
        LogUtil.getInstance().stopLogger(loggerName);
    }

}
