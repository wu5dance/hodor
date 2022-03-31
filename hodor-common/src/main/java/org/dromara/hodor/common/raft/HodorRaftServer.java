package org.dromara.hodor.common.raft;

import cn.hutool.core.lang.Assert;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.server.impl.ServerImplUtils;
import org.apache.ratis.statemachine.StateMachine;
import org.apache.ratis.util.NetUtils;

/**
 * HodorRaftServer
 *
 * @author tomgs
 * @version 2022/3/20 1.0
 */
public class HodorRaftServer {

    private final Map<RaftGroupId, StateMachine> registry;

    private final RaftOptions raftOptions;

    private final String endpoint;

    private final File storageDir;

    private RaftServer server;

    public HodorRaftServer(final RaftOptions raftOptions) throws IOException {
        Assert.notNull(raftOptions, "raftOptions must not be null.");
        this.raftOptions = raftOptions;
        this.endpoint = raftOptions.getEndpoint();
        this.storageDir = raftOptions.getStorageDir();
        this.registry = raftOptions.getStateMachineMap()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(e ->
                    e.getKey().getRaftGroupId(),
                Map.Entry::getValue));
        init();
    }

    private void init() throws IOException {
        //create a property object
        RaftProperties properties = new RaftProperties();

        RaftPeer currentPeer = RaftGroupManager.getInstance().buildRaftPeer(NetUtils.createSocketAddr(endpoint), 0);
        //set the storage directory (different for each peer) in RaftProperty object
        RaftServerConfigKeys.setStorageDir(properties, Collections.singletonList(new File(storageDir, currentPeer.getId().toString())));
        //set the port which server listen to in RaftProperty object
        int port = NetUtils.createSocketAddr(currentPeer.getAddress()).getPort();
        GrpcConfigKeys.Server.setPort(properties, port);
        //create and start the Raft server
        this.server = ServerImplUtils.newRaftServer(currentPeer.getId(), null, registry::get, properties, null);
    }

    public void start() throws IOException {
        this.server.start();
    }

    public void stop() throws IOException {
        this.server.close();
    }

}
