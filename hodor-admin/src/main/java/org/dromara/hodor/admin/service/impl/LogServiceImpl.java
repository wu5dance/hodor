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

package org.dromara.hodor.admin.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.hodor.admin.service.ActuatorOperatorService;
import org.dromara.hodor.admin.service.LogService;
import org.dromara.hodor.client.model.LogQueryRequest;
import org.dromara.hodor.client.model.LogQueryResult;
import org.springframework.stereotype.Service;

/**
 * LogServiceImpl
 *
 * @author tomgs
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
public class LogServiceImpl implements LogService {

    private final ActuatorOperatorService actuatorOperatorService;

    @Override
    public LogQueryResult queryLog(LogQueryRequest request) throws Exception {
        return actuatorOperatorService.queryLog(request);
    }

}
