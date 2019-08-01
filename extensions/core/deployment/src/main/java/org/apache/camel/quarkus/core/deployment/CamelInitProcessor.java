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
package org.apache.camel.quarkus.core.deployment;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.BeanContainerListenerBuildItem;
import io.quarkus.arc.deployment.RuntimeBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.runtime.RuntimeValue;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.quarkus.core.runtime.CamelConfig;
import org.apache.camel.quarkus.core.runtime.CamelConfig.BuildTime;
import org.apache.camel.quarkus.core.runtime.CamelProducers;
import org.apache.camel.quarkus.core.runtime.CamelRecorder;
import org.apache.camel.quarkus.core.runtime.CamelRuntime;
import org.apache.camel.quarkus.core.runtime.support.RuntimeRegistry;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.slf4j.LoggerFactory;

class CamelInitProcessor {

    @Inject
    ApplicationArchivesBuildItem applicationArchivesBuildItem;
    @Inject
    CombinedIndexBuildItem combinedIndexBuildItem;
    @Inject
    BuildTime buildTimeConfig;

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep(applicationArchiveMarkers = { CamelSupport.CAMEL_SERVICE_BASE_PATH, CamelSupport.CAMEL_ROOT_PACKAGE_DIRECTORY })
    CamelRuntimeBuildItem createInitTask(RecorderContext recorderContext, CamelRecorder recorder,
            BuildProducer<RuntimeBeanBuildItem> runtimeBeans) {
        Properties properties = new Properties();
        Config configProvider = ConfigProvider.getConfig();
        for (String property : configProvider.getPropertyNames()) {
            if (property.startsWith("camel.")) {
                properties.put(property, configProvider.getValue(property, String.class));
            }
            if (property.startsWith("integration.")) {
                properties.put(property.substring("integration.".length()), configProvider.getValue(property, String.class));
            }
        }

        RuntimeRegistry registry = new RuntimeRegistry();
        final List<RuntimeValue<?>> builders;
        if (buildTimeConfig.deferInitPhase) {
            builders = getBuildTimeRouteBuilderClasses().map(recorderContext::newInstance)
                    .collect(Collectors.toList());
        } else {
            builders = new ArrayList<>();
        }

        visitServices((name, type) -> {
            LoggerFactory.getLogger(CamelInitProcessor.class).debug("Binding camel service {} with type {}", name, type);
            registry.bind(name, type,
                    recorderContext.newInstance(type.getName()));
        });

        RuntimeValue<CamelRuntime> camelRuntime = recorder.create(registry, properties, builders, buildTimeConfig);

        runtimeBeans
                .produce(RuntimeBeanBuildItem.builder(CamelRuntime.class).setRuntimeValue(camelRuntime).build());

        return new CamelRuntimeBuildItem(camelRuntime);
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep(applicationArchiveMarkers = { CamelSupport.CAMEL_SERVICE_BASE_PATH, CamelSupport.CAMEL_ROOT_PACKAGE_DIRECTORY })
    AdditionalBeanBuildItem createCamelProducers(
            RecorderContext recorderContext,
            CamelRuntimeBuildItem runtime,
            CamelRecorder recorder,
            BuildProducer<BeanContainerListenerBuildItem> listeners) {

        listeners
                .produce(new BeanContainerListenerBuildItem(recorder.initRuntimeInjection(runtime.getRuntime())));

        return AdditionalBeanBuildItem.unremovableOf(CamelProducers.class);
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep(applicationArchiveMarkers = { CamelSupport.CAMEL_SERVICE_BASE_PATH, CamelSupport.CAMEL_ROOT_PACKAGE_DIRECTORY })
    void initTask(
            BeanContainerBuildItem beanContainerBuildItem,
            CamelRuntimeBuildItem runtime,
            CamelRecorder recorder) throws Exception {

        final List<String> builders;
        if (!buildTimeConfig.deferInitPhase) {
            builders = getBuildTimeRouteBuilderClasses().collect(Collectors.toList());
        } else {
            builders = new ArrayList<>();
        }
        recorder.init(beanContainerBuildItem.getValue(), runtime.getRuntime(), builders, buildTimeConfig);
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(applicationArchiveMarkers = { CamelSupport.CAMEL_SERVICE_BASE_PATH, CamelSupport.CAMEL_ROOT_PACKAGE_DIRECTORY })
    void createRuntimeInitTask(
            CamelRecorder recorder,
            CamelRuntimeBuildItem runtime,
            ShutdownContextBuildItem shutdown,
            CamelConfig.Runtime runtimeConfig)
            throws Exception {

        recorder.start(shutdown, runtime.getRuntime(), runtimeConfig);
    }

    protected Stream<String> getBuildTimeRouteBuilderClasses() {
        Set<ClassInfo> allKnownImplementors = new HashSet<>();
        allKnownImplementors.addAll(
                combinedIndexBuildItem.getIndex().getAllKnownImplementors(DotName.createSimple(RoutesBuilder.class.getName())));
        allKnownImplementors.addAll(
                combinedIndexBuildItem.getIndex().getAllKnownSubclasses(DotName.createSimple(RouteBuilder.class.getName())));
        allKnownImplementors.addAll(combinedIndexBuildItem.getIndex()
                .getAllKnownSubclasses(DotName.createSimple(AdviceWithRouteBuilder.class.getName())));

        return allKnownImplementors
                .stream()
                .filter(CamelSupport::isConcrete)
                .filter(CamelSupport::isPublic)
                .map(ClassInfo::toString);
    }

    protected void visitServices(BiConsumer<String, Class<?>> consumer) {
        CamelSupport.resources(applicationArchivesBuildItem, CamelSupport.CAMEL_SERVICE_BASE_PATH)
                .forEach(p -> visitService(p, consumer));
    }

    protected void visitService(Path p, BiConsumer<String, Class<?>> consumer) {
        String name = p.getFileName().toString();
        try (InputStream is = Files.newInputStream(p)) {
            Properties props = new Properties();
            props.load(is);
            for (Map.Entry<Object, Object> entry : props.entrySet()) {
                String k = entry.getKey().toString();
                if (k.equals("class")) {
                    String clazz = entry.getValue().toString();
                    Class<?> cl = Class.forName(clazz);

                    consumer.accept(name, cl);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
