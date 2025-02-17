/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.dromara.hodor.actuator.bigdata.jobtype.hiveutils;

/**
 * Thrown when unexpected Hive metastore browsing problems come up
 */
public class HiveMetaStoreBrowserException extends Exception {
  private static final long serialVersionUID = 1L;

  public HiveMetaStoreBrowserException(String msg) {
    super(msg);
  }

  public HiveMetaStoreBrowserException(Throwable t) {
    super(t);
  }

  public HiveMetaStoreBrowserException(String msg, Throwable t) {
    super(msg, t);
  }
}
