/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.hodor.register.embedded;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.apache.ratis.RaftConfigKeys;
import org.apache.ratis.conf.Parameters;
import org.apache.ratis.conf.RaftProperties;
import org.dromara.hodor.common.HodorLifecycle;
import org.dromara.hodor.common.raft.RaftOptions;
import org.dromara.hodor.common.raft.kv.core.HodorKVOptions;
import org.dromara.hodor.common.raft.kv.storage.DBProfile;
import org.dromara.hodor.common.raft.kv.storage.StorageOptions;
import org.dromara.hodor.common.raft.kv.storage.StorageType;
import org.dromara.hodor.register.api.RegistryConfig;
import org.dromara.hodor.register.embedded.core.WatchManager;
import org.dromara.hodor.register.embedded.watch.HodorWatchServer;
import org.dromara.hodor.register.embedded.watch.WatchGrpcRpcType;

/**
 * EmbeddedRegistryServer
 *
 * @author tomgs
 * @since 1.0
 */
@Slf4j
public class EmbeddedRegistryServer implements HodorLifecycle {

    private final HodorWatchServer hodorWatchServer;

    public EmbeddedRegistryServer(final RegistryConfig registryConfig, final WatchManager watchManager) throws Exception {
        RaftProperties raftProperties = new RaftProperties();
        RaftConfigKeys.Rpc.setType(raftProperties, WatchGrpcRpcType.INSTANCE);
        RaftOptions raftOptions = RaftOptions.builder()
            .endpoint(registryConfig.getEndpoint())
            .storageDir(new File(registryConfig.getDataPath()))
            .serverAddresses(registryConfig.getServers())
            .raftProperties(raftProperties)
            .parameters(new Parameters())
            //.stateMachineMap(stateMachineMap) // for customer
            .build();
        StorageOptions storageOptions = StorageOptions.builder()
            .storageType(StorageType.RocksDB)
            .storageProfile(DBProfile.DISK)
            .storagePath(new File(registryConfig.getDataPath()))
            .build();
        HodorKVOptions kvOptions = HodorKVOptions.builder()
            .clusterName(registryConfig.getNamespace())
            .raftOptions(raftOptions)
            .storageOptions(storageOptions)
            .build();
        this.hodorWatchServer = new HodorWatchServer(kvOptions, watchManager);
    }

    @Override
    public void start() throws Exception {
        log.info("registry server starting ...");
        hodorWatchServer.start();
    }

    @Override
    public void stop() throws Exception {
        log.info("registry server closing ...");
        hodorWatchServer.stop();
    }

}
