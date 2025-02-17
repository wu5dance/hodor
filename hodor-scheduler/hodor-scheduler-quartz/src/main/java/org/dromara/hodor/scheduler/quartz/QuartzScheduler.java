package org.dromara.hodor.scheduler.quartz;

import cn.hutool.core.util.ReflectUtil;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;
import org.dromara.hodor.common.extension.Join;
import org.dromara.hodor.model.job.JobDesc;
import org.dromara.hodor.scheduler.api.HodorScheduler;
import org.dromara.hodor.scheduler.api.JobExecutor;
import org.dromara.hodor.scheduler.api.common.SchedulerConfig;
import org.dromara.hodor.scheduler.api.exception.HodorSchedulerException;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.core.QuartzSchedulerResources;
import org.quartz.impl.StdSchedulerFactory;

/**
 *  implements scheduler by quartz
 *
 * @author tomgs
 * @since 1.0
 */
@Join
public class QuartzScheduler implements HodorScheduler {

    private String schedulerName;
    private Scheduler scheduler;
    private org.quartz.core.QuartzScheduler rawScheduler;
    private final ReentrantLock lock;
    private final StdSchedulerFactory factory;

    public QuartzScheduler() {
        factory = new StdSchedulerFactory();
        lock = new ReentrantLock();
    }

    public QuartzScheduler(SchedulerConfig config) {
        this();
        config(config);
    }

    @Override
    public void config(SchedulerConfig config) {
        try {
            this.factory.initialize(getBaseProperties(config));
            this.scheduler = factory.getScheduler();
            this.schedulerName = config.getSchedulerName();
            this.rawScheduler = (org.quartz.core.QuartzScheduler) ReflectUtil.getFieldValue(scheduler, "sched");
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        }
    }

    private Properties getBaseProperties(SchedulerConfig config) {
        Properties result = new Properties();
        result.put("org.quartz.threadPool.class", org.quartz.simpl.SimpleThreadPool.class.getName());
        result.put("org.quartz.threadPool.threadCount", String.valueOf(config.getThreadCount()));
        result.put("org.quartz.scheduler.instanceName", config.getSchedulerName());
        result.put("org.quartz.jobStore.misfireThreshold", String.valueOf(config.getMisfireThreshold()));
        return result;
    }

    @Override
    public String getSchedulerName() {
        return schedulerName;
    }

    @Override
    public void start() {
        lock.lock();
        try {
            scheduler.start();
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void standby() {
        try {
            scheduler.standby();
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        }
    }

    @Override
    public void shutdown() {
        try {
            scheduler.shutdown();
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        }
    }

    @Override
    public void startDelayed(int seconds) {
        try {
            scheduler.startDelayed(seconds);
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        }
    }

    @Override
    public void addJob(JobDesc jobDesc, JobExecutor jobExecutor) {
        if (checkExists(jobDesc)) {
            return;
        }
        JobDetail jobDetail = JobBuilder.newJob(HodorJob.class)
                .withIdentity(jobDesc.getJobName(), jobDesc.getGroupName())
                .requestRecovery(true)
                .build();

        jobDetail.getJobDataMap().put("schedulerName", schedulerName);
        jobDetail.getJobDataMap().put("jobExecutor", jobExecutor);
        jobDetail.getJobDataMap().put("jobDesc", jobDesc);

        TriggerBuilder<CronTrigger> triggerBuilder = TriggerBuilder.newTrigger()
            .withIdentity(jobDesc.getJobName(), jobDesc.getGroupName())
            .withSchedule(CronScheduleBuilder.cronSchedule(jobDesc.getCron()).withMisfireHandlingInstructionDoNothing())
            .withPriority(jobDesc.getPriority().getValue())
            .forJob(jobDetail);
        if (jobDesc.getFireNow()) {
            triggerBuilder = triggerBuilder.startNow();
        }
        if (jobDesc.getEndTime() != null) {
            triggerBuilder = triggerBuilder.endAt(jobDesc.getEndTime());
        }
        Trigger trigger = triggerBuilder.build();

        try {
            scheduler.scheduleJob(jobDetail, trigger);
            if (jobDesc.getFireNow()) {
                scheduler.triggerJob(jobDetail.getKey());
            }
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        }
    }

    @Override
    public boolean deleteJob(JobDesc jobDesc) {
        try {
            if (scheduler.checkExists(JobKey.jobKey(jobDesc.getJobName(), jobDesc.getGroupName()))) {
                return scheduler.deleteJob(JobKey.jobKey(jobDesc.getJobName(), jobDesc.getGroupName()));
            }
            return false;
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        }
    }

    @Override
    public boolean deleteJobs(List<JobDesc> jobDescList) {
        boolean result = true;
        for (JobDesc jobDesc : jobDescList) {
            result = deleteJob(jobDesc);
        }
        return result;
    }

    @Override
    public boolean checkExists(JobDesc jobDesc) {
        try {
            return scheduler.checkExists(JobKey.jobKey(jobDesc.getJobName(), jobDesc.getGroupName()));
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        }
    }

    @Override
    public void pauseJob(JobDesc jobDesc) {
        try {
            scheduler.pauseJob(JobKey.jobKey(jobDesc.getJobName(), jobDesc.getGroupName()));
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        }
    }

    @Override
    public boolean isPaused(JobDesc jobDesc) {
        try {
            return scheduler.isShutdown() && Trigger.TriggerState.PAUSED == scheduler.getTriggerState(new TriggerKey(jobDesc.getJobName(), jobDesc.getGroupName()));
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        }
    }

    @Override
    public void clear() {
        try {
            scheduler.clear();
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        }
    }

    @Override
    public void resumeJob(JobDesc jobDesc) {
        try {
            scheduler.resumeJob(JobKey.jobKey(jobDesc.getJobName(), jobDesc.getGroupName()));
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        }
    }

    @Override
    public void triggerJob(JobDesc jobDesc) {
        try {
            scheduler.triggerJob(JobKey.jobKey(jobDesc.getJobName(), jobDesc.getGroupName()));
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        }
    }

    @Override
    public boolean isShutdown() {
        try {
            return scheduler.isShutdown();
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        }
    }

    @Override
    public boolean isStandby() {
        try {
            return scheduler.isInStandbyMode();
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        }
    }

    @Override
    public int getNumberOfJobs() {
        try {
            QuartzSchedulerResources resources = (QuartzSchedulerResources) ReflectUtil.getFieldValue(rawScheduler, "resources");
            return resources.getJobStore().getNumberOfJobs();
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        }
    }

    @Override
    public boolean isStarted() {
        try {
            return scheduler.isStarted();
        } catch (SchedulerException e) {
            throw new HodorSchedulerException(e);
        }
    }

}
