package org.dromara.hodor.client.handler;

import lombok.extern.slf4j.Slf4j;
import org.dromara.hodor.client.ServiceProvider;
import org.dromara.hodor.client.core.RequestContext;
import org.dromara.hodor.client.executor.RequestHandleManager;
import org.dromara.hodor.remoting.api.HodorChannel;
import org.dromara.hodor.remoting.api.HodorChannelHandler;
import org.dromara.hodor.remoting.api.RemotingMessageSerializer;
import org.dromara.hodor.remoting.api.message.RemotingMessage;

/**
 * job execute request handler
 *
 * @author tomgs
 * @since 2021/1/7
 */
@Slf4j
public class JobRequestHandler implements HodorChannelHandler {

    private final RequestHandleManager requestHandleManager = ServiceProvider.getInstance().getBean(RequestHandleManager.class);

    private final RemotingMessageSerializer serializer = ServiceProvider.getInstance().getBean(RemotingMessageSerializer.class);

    @Override
    public void received(HodorChannel channel, Object message) {
        final RemotingMessage request = (RemotingMessage) message;
        final RequestContext context = new RequestContext(channel, request, serializer);
        this.requestHandleManager.notifyRequestHandler(context);
    }

    @Override
    public void exceptionCaught(HodorChannel channel, Throwable cause) {
        log.error("handler the request message has exception, message: {}.", cause.getMessage(), cause);
        //channel.send(cause).operationComplete(future -> future.channel().close());
        channel.close();
    }

}