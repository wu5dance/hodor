/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.dromara.hodor.common.raft.kv.core;

import java.util.List;
import org.apache.ratis.server.protocol.TermIndex;
import org.apache.ratis.server.storage.FileInfo;
import org.apache.ratis.statemachine.SnapshotInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class captures the snapshotIndex and term of the latest snapshot in
 * the server.
 * Ratis server loads the snapshotInfo during startup and updates the
 * lastApplied index to this snapshotIndex. SnapshotInfo does not contain
 * any files. It is used only to store/ update the last applied index and term.
 */
public class HodorKVSnapshotInfo implements SnapshotInfo {

  private static final Logger LOG = LoggerFactory.getLogger(HodorKVSnapshotInfo.class);

  public static final String TRANSACTION_INFO_SPLIT_KEY = "#";

  private volatile long term = 0;
  private volatile long snapshotIndex = -1;

  public void updateTerm(long newTerm) {
    term = newTerm;
  }

  public void updateTermIndex(long newTerm, long newIndex) {
    this.term = newTerm;
    this.snapshotIndex = newIndex;
  }

  public HodorKVSnapshotInfo() { }

  public HodorKVSnapshotInfo(long term, long index) {
    this.term = term;
    this.snapshotIndex = index;
  }

  @Override
  public TermIndex getTermIndex() {
    return TermIndex.valueOf(term, snapshotIndex);
  }

  @Override
  public long getTerm() {
    return term;
  }

  @Override
  public long getIndex() {
    return snapshotIndex;
  }

  @Override
  public List<FileInfo> getFiles() {
    return null;
  }

  @Override
  public String toString() {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(term);
    stringBuilder.append(TRANSACTION_INFO_SPLIT_KEY);
    stringBuilder.append(snapshotIndex);
    return stringBuilder.toString();
  }
}
