/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dromara.hodor.register.embedded.watch;

import java.util.function.Consumer;
import org.apache.ratis.client.ClientFactory;
import org.apache.ratis.conf.Parameters;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.grpc.GrpcTlsConfig;
import org.apache.ratis.grpc.client.GrpcClientRpc;
import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.ServerFactory;
import org.apache.ratis.server.leader.FollowerInfo;
import org.apache.ratis.server.leader.LeaderState;
import org.apache.ratis.server.leader.LogAppender;
import org.apache.ratis.thirdparty.io.netty.buffer.PooledByteBufAllocator;
import org.apache.ratis.util.JavaUtils;
import org.dromara.hodor.register.embedded.client.GrpcWatchClientRpc;
import org.dromara.hodor.register.embedded.client.WatchClientFactory;
import org.dromara.hodor.register.embedded.client.WatchClientRpc;
import org.dromara.hodor.register.embedded.core.WatchManager;
import org.dromara.hodor.register.embedded.service.WatchGrpcLogAppender;
import org.dromara.hodor.register.embedded.service.WatchGrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WatchGrpcFactory implements ServerFactory, ClientFactory, WatchClientFactory {

  public static final Logger LOG = LoggerFactory.getLogger(WatchGrpcFactory.class);

  static final String USE_CACHE_FOR_ALL_THREADS_NAME = "useCacheForAllThreads";
  static final String USE_CACHE_FOR_ALL_THREADS_KEY = "org.apache.ratis.thirdparty.io.netty.allocator."
      + USE_CACHE_FOR_ALL_THREADS_NAME;
  static {
    // see org.apache.ratis.thirdparty.io.netty.buffer.PooledByteBufAllocator.DEFAULT_USE_CACHE_FOR_ALL_THREADS
    final String value = JavaUtils.getSystemProperty(USE_CACHE_FOR_ALL_THREADS_KEY);
    if (value == null) {
      // Set the property to false, when it is not set.
      JavaUtils.setSystemProperty(USE_CACHE_FOR_ALL_THREADS_KEY, Boolean.FALSE.toString());
    }
  }

    private final Parameters parameters;

    static boolean checkPooledByteBufAllocatorUseCacheForAllThreads(Consumer<String> log) {
    final boolean value = PooledByteBufAllocator.defaultUseCacheForAllThreads();
    if (value) {
      log.accept("PERFORMANCE WARNING: " + USE_CACHE_FOR_ALL_THREADS_NAME + " is " + true
          + " that may cause Netty to create a lot garbage objects and, as a result, trigger GC.\n"
          + "\tIt is recommended to disable " + USE_CACHE_FOR_ALL_THREADS_NAME
          + " by setting -D" + USE_CACHE_FOR_ALL_THREADS_KEY + "=" + false + " in command line.");
    }
    return value;
  }

  private final GrpcTlsConfig tlsConfig;
  private final GrpcTlsConfig adminTlsConfig;
  private final GrpcTlsConfig clientTlsConfig;
  private final GrpcTlsConfig serverTlsConfig;

  public static Parameters newRaftParameters(GrpcTlsConfig conf) {
    final Parameters p = new Parameters();
    GrpcConfigKeys.TLS.setConf(p, conf);
    return p;
  }

  public WatchGrpcFactory(Parameters parameters) {
    this(
        parameters,
        GrpcConfigKeys.TLS.conf(parameters),
        GrpcConfigKeys.Admin.tlsConf(parameters),
        GrpcConfigKeys.Client.tlsConf(parameters),
        GrpcConfigKeys.Server.tlsConf(parameters)
    );
  }

  public WatchGrpcFactory(GrpcTlsConfig tlsConfig) {
    this(null, tlsConfig, null, null, null);
  }

  private WatchGrpcFactory(Parameters parameters, GrpcTlsConfig tlsConfig, GrpcTlsConfig adminTlsConfig,
                           GrpcTlsConfig clientTlsConfig, GrpcTlsConfig serverTlsConfig) {
      this.parameters = parameters;
    this.tlsConfig = tlsConfig;
    this.adminTlsConfig = adminTlsConfig;
    this.clientTlsConfig = clientTlsConfig;
    this.serverTlsConfig = serverTlsConfig;
  }

  public GrpcTlsConfig getTlsConfig() {
    return tlsConfig;
  }

  public GrpcTlsConfig getAdminTlsConfig() {
    return adminTlsConfig != null ? adminTlsConfig : tlsConfig;
  }

  public GrpcTlsConfig getClientTlsConfig() {
    return clientTlsConfig != null ? clientTlsConfig : tlsConfig;
  }

  public GrpcTlsConfig getServerTlsConfig() {
    return serverTlsConfig != null ? serverTlsConfig : tlsConfig;
  }

  @Override
  public WatchGrpcRpcType getRpcType() {
    return WatchGrpcRpcType.INSTANCE;
  }

  @Override
  public LogAppender newLogAppender(RaftServer.Division server, LeaderState state, FollowerInfo f) {
    return new WatchGrpcLogAppender(server, state, f);
  }

  @Override
  public WatchGrpcService newRaftServerRpc(RaftServer server) {
    checkPooledByteBufAllocatorUseCacheForAllThreads(LOG::info);
    return WatchGrpcService.newBuilder()
        .setServer(server)
        .setWatchManager(getWatchManager())
        .setAdminTlsConfig(getAdminTlsConfig())
        .setServerTlsConfig(getServerTlsConfig())
        .setClientTlsConfig(getClientTlsConfig())
        .build();
  }

    private WatchManager getWatchManager() {
        return parameters.get(WatchManager.class.getName(), WatchManager.class);
    }

    @Override
  public GrpcClientRpc newRaftClientRpc(ClientId clientId, RaftProperties properties) {
    checkPooledByteBufAllocatorUseCacheForAllThreads(LOG::debug);
    return new GrpcClientRpc(clientId, properties,
        getAdminTlsConfig(), getClientTlsConfig());
  }

  @Override
  public WatchClientRpc newWatchClientRpc(ClientId clientId, RaftProperties properties) {
    checkPooledByteBufAllocatorUseCacheForAllThreads(LOG::debug);
    return new GrpcWatchClientRpc(clientId, properties);
  }

}
