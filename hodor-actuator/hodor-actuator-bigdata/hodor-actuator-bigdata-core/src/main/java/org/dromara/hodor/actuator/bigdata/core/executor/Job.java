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

package org.dromara.hodor.actuator.bigdata.core.executor;

import org.dromara.hodor.actuator.api.utils.Props;

/**
 * Raw job interface.
 *
 * A job is unit of work to perform.
 *
 * A job is required to have a constructor Job(String jobId, Props props)
 */

public interface Job {

  /**
   * Returns a unique(should be checked in xml) string name/id for the Job.
   */
  String getId();

  /**
   * execute the job. In general this method can only be run once. Must either succeed or throw an
   * exception.
   */
  void run() throws Exception;

  /**
   * Best effort attempt to cancel the job.
   *
   * @throws Exception If cancel fails
   */
  void cancel() throws Exception;

  /**
   * Returns a progress report between [0 - 1.0] to indicate the percentage complete
   *
   * @throws Exception If getting progress fails
   */
  double getProgress() throws Exception;

  /**
   * Get the generated properties from this job.
   */
  Props getJobGeneratedProperties();

  /**
   * Determine if the job was cancelled.
   */
  boolean isCanceled();
}
