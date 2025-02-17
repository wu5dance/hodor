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

public class HiveQueryException extends Exception {
  private static final long serialVersionUID = 1L;
  private final String query;
  private final int code;
  private final String message;

  public HiveQueryException(String query, int code, String message) {
    this.query = query;
    this.code = code;
    this.message = message;
  }

  public int getCode() {
    return code;
  }

  @Override
  public String getMessage() {
    return message;
  }

  public String getQuery() {
    return query;
  }

  @Override
  public String toString() {
    return "HiveQueryException{" + "query='" + query + '\'' + ", code=" + code
        + ", message='" + message + '\'' + '}';
  }
}
