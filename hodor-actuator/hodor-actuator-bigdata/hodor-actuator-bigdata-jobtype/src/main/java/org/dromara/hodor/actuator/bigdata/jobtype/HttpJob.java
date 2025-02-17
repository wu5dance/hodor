package org.dromara.hodor.actuator.bigdata.jobtype;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.dromara.hodor.actuator.bigdata.core.executor.ProcessJob;
import org.dromara.hodor.actuator.api.utils.Props;

/**
 * @author tomgs
 * @since 1.0
 **/
public class HttpJob extends ProcessJob {

    public static final String URL = "url";
    public static final String CURL = "curl ";

    public HttpJob(String jobId, Props sysProps, Props jobProps, Logger log) {
        super(jobId, sysProps, jobProps, log);
    }

    @Override
    protected List<String> getCommandList() {
        final List<String> commands = new ArrayList<>();
        commands.add(CURL + this.jobProps.getString(URL));
        for (int i = 1; this.jobProps.containsKey(URL + "." + i); i++) {
            commands.add(CURL + this.jobProps.getString(URL + "." + i));
        }

        return commands;
    }
}
