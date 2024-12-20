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
package org.apache.nifi.cluster.coordination.http.endpoints;

import org.apache.nifi.cluster.coordination.http.EndpointResponseMerger;
import org.apache.nifi.cluster.manager.FlowRegistryClientEntityMerger;
import org.apache.nifi.cluster.manager.NodeResponse;
import org.apache.nifi.cluster.protocol.NodeIdentifier;
import org.apache.nifi.web.api.entity.FlowRegistryClientEntity;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class FlowRegistryClientEndpointMerger extends AbstractSingleEntityEndpoint<FlowRegistryClientEntity> implements EndpointResponseMerger {
    public static final String CONTROLLER_REGISTRY_URI = "/nifi-api/controller/registry-clients";
    public static final Pattern CONTROLLER_REGISTRY_URI_PATTERN = Pattern.compile("/nifi-api/controller/registry-clients/[a-f0-9\\-]{36}");
    public static final Set<String> CONTROLLER_REGISTRY_ACCEPTED_METHODS = new HashSet<>(Arrays.asList("GET", "PUT", "DELETE"));
    private final FlowRegistryClientEntityMerger flowRegistryClientEntityMerger = new FlowRegistryClientEntityMerger();

    @Override
    public boolean canHandle(final URI uri, final String method) {
        if (CONTROLLER_REGISTRY_ACCEPTED_METHODS.contains(method) && CONTROLLER_REGISTRY_URI_PATTERN.matcher(uri.getPath()).matches()) {
            return true;
        } else if ("POST".equalsIgnoreCase(method) && CONTROLLER_REGISTRY_URI.equals(uri.getPath())) {
            return true;
        }

        return false;
    }

    @Override
    protected Class<FlowRegistryClientEntity> getEntityClass() {
        return FlowRegistryClientEntity.class;
    }

    @Override
    protected void mergeResponses(
            final FlowRegistryClientEntity clientEntity, final Map<NodeIdentifier, FlowRegistryClientEntity> entityMap,
            final Set<NodeResponse> successfulResponses, final Set<NodeResponse> problematicResponses) {
        flowRegistryClientEntityMerger.merge(clientEntity, entityMap);
    }
}
