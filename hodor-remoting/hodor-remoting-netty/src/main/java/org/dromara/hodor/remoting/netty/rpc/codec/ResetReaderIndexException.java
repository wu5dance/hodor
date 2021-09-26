package org.dromara.hodor.remoting.netty.rpc.codec;

/**
 * ResetReaderIndexException
 *
 * @author tomgs
 * @since 2021/8/4
 */
public class ResetReaderIndexException extends Exception {

    private static final long serialVersionUID = -2633801760428109833L;

    public ResetReaderIndexException(String msg) {
        super(msg);
    }

}
