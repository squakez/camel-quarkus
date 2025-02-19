/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.component.telemetry.dev;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.camel.telemetrydev.TelemetryDevTracer;

@Singleton
public class TelemetryDevTracerProducer {

    @Inject
    CamelTelemetryDevConfig config;

    @Produces
    @Singleton
    @DefaultBean
    public TelemetryDevTracer getOpenTelemetry() {
        TelemetryDevTracer telemetryDevTracer = new TelemetryDevTracer();
        if (config.excludePatterns().isPresent()) {
            telemetryDevTracer.setExcludePatterns(config.excludePatterns().get());
        }

        if (config.traceProcessors()) {
            telemetryDevTracer.setTraceProcessors(config.traceProcessors());
        }

        if (config.traceFormat().isPresent()) {
            telemetryDevTracer.setTraceFormat(config.traceFormat().get());
        }
        return telemetryDevTracer;
    }
}
