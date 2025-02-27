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
package org.apache.camel.quarkus.component.jolokia.it;

import java.time.Duration;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@TestProfile(JolokiaCustomLogHandlerTest.JolokiaCustomLogHandlerProfile.class)
@QuarkusTest
class JolokiaCustomLogHandlerTest {
    @Test
    void customLogHandler() {
        Awaitility.await().pollInterval(Duration.ofMillis(250)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            RestAssured.get("/jolokia/custom/logger/activated")
                    .then()
                    .statusCode(200);
        });
    }

    public static final class JolokiaCustomLogHandlerProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.camel.jolokia.additional-properties.logHandlerClass", CustomLogHandler.class.getName());
        }
    }
}
