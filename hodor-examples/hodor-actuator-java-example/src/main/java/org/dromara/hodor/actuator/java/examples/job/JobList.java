package org.dromara.hodor.actuator.java.examples.job;

import org.apache.logging.log4j.Logger;
import org.dromara.hodor.actuator.api.JobExecutionContext;
import org.dromara.hodor.actuator.java.annotation.Job;
import org.springframework.stereotype.Component;

/**
 * job list demo
 *
 * @author tomgs
 * @since 1.0
 */
@Component
public class JobList {

    @Job(group = "testGroup", jobName = "test1", cron = "0/5 * * * * ?")
    public String test1(JobExecutionContext context) {
        System.out.println(context);
        Logger logger = context.getJobLogger();
        logger.info("start executor job test1");
        logger.info("job argument: {}", context.getJobParameter());
        logger.info("executing......");
        logger.info("executed");
        return "a=123";
    }

    @Job(group = "testGroup", jobName = "test2", cron = "0/5 * * * * ?")
    public void test2() {
        System.out.println("no arguments ...");
    }

}
