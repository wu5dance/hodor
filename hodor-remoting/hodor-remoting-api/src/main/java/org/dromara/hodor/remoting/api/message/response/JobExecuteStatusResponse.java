package org.dromara.hodor.remoting.api.message.response;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.dromara.hodor.common.utils.StringUtils;
import org.dromara.hodor.model.enums.JobExecuteStatus;

/**
 *  job execute status response
 *
 * @author tomgs
 * @version 2021/3/3 1.0 
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class JobExecuteStatusResponse extends AbstractResponseBody {

    private static final long serialVersionUID = 8892404430523254103L;

    private JobExecuteStatus status;

    private String startTime;

    private String completeTime;

    private String comments;

    private byte[] result;

    @Override
    public String toString() {
        return "JobExecuteStatusResponse{" +
            "requestId=" + getRequestId() + '\'' +
            ",status=" + status + '\'' +
            ", startTime='" + startTime + '\'' +
            ", completeTime='" + completeTime + '\'' +
            ", comments='" + comments + '\'' +
            ", result='" + StringUtils.decodeString(result) + '\'' +
            '}';
    }

}
