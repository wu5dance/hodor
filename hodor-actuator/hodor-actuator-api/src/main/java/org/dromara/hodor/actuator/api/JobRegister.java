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

package org.dromara.hodor.actuator.api;

import java.util.List;
import java.util.Set;
import org.dromara.hodor.actuator.api.core.ExecutableJobContext;
import org.dromara.hodor.actuator.api.core.JobInstance;
import org.dromara.hodor.model.job.JobDesc;

/**
 * JobRegister
 *
 * @author tomgs
 * @since 1.0
 */
public interface JobRegister {

    /**
     * Binding group name set，Preferred to use this, when config bindingCluster.
     *
     * @return groupName set
     */
    Set<String> bindingGroup();

    /**
     * binding cluster name
     *
     * @return cluster name
     */
    String bindingCluster();

    List<String> registerJobType();

    /**
     * register job to scheduler
     *
     * @throws Exception register exception
     */
    List<JobDesc> registerJobs() throws Exception;

    /**
     * register job
     *
     * @param jobInstance job instance
     */
    default void registerJob(JobInstance jobInstance) {

    }

    /**
     * provider job runnable by executable job context
     *
     * @param context executable job context
     * @return job runnable
     */
    ExecutableJob provideExecutableJob(ExecutableJobContext context) throws Exception;

    /**
     * clear register jobs cache
     */
    default void clear() {

    }

}
