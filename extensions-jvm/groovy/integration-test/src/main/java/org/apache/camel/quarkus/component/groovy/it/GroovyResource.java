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
package org.apache.camel.quarkus.component.groovy.it;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.camel.CamelContext;
import org.jboss.logging.Logger;

@Path("/groovy")
@ApplicationScoped
public class GroovyResource {

    private static final Logger LOG = Logger.getLogger(GroovyResource.class);

    private static final String LANGUAGE_GROOVY = "groovy";
    @Inject
    CamelContext context;

    @Path("/load/language/groovy")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response loadLanguageGroovy() throws Exception {
        /* This is an autogenerated test */
        if (context.resolveLanguage(LANGUAGE_GROOVY) != null) {
            return Response.ok().build();
        }
        LOG.warnf("Could not load [%s] from the Camel context", LANGUAGE_GROOVY);
        return Response.status(500, LANGUAGE_GROOVY + " could not be loaded from the Camel context").build();
    }
}
