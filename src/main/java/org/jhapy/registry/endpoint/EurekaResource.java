/*
 * Copyright 2020-2020 the original author or authors from the JHapy project.
 *
 * This file is part of the JHapy project, see https://www.jhapy.org/ for more information.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jhapy.registry.endpoint;

import static java.util.stream.Collectors.toMap;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.PortType;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Pair;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerContextHolder;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.registry.PeerAwareInstanceRegistryImpl;
import com.netflix.eureka.resources.StatusResource;
import com.netflix.eureka.util.StatusInfo;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jhapy.commons.endpoint.BaseEndpoint;
import org.jhapy.commons.utils.OrikaBeanMapper;
import org.jhapy.dto.registry.EurekaApplication;
import org.jhapy.dto.registry.EurekaApplicationInstance;
import org.jhapy.dto.registry.EurekaInfo;
import org.jhapy.dto.registry.EurekaStatus;
import org.jhapy.dto.serviceQuery.ServiceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for viewing Eureka data.
 */
@RestController
@RequestMapping("/api")
public class EurekaResource {

    private final Logger log = LoggerFactory.getLogger(EurekaResource.class);

    /**
     * GET  /eureka/applications : get Eureka applications information
     */
    @PostMapping(value = "/eureka/applications", produces = "application/json")
    public ResponseEntity<ServiceResult> eureka() {
        EurekaInfo eurekaVM = new EurekaInfo();
        eurekaVM.setApplicationList(getApplications());
        return ResponseEntity.ok(new ServiceResult( eurekaVM ));
    }

    private List<EurekaApplication> getApplications() {
        List<Application> sortedApplications = getRegistry().getSortedApplications();
        List<EurekaApplication> apps = new ArrayList<>();
        for (Application app : sortedApplications) {
            EurekaApplication eurekaApplication = new EurekaApplication();
            eurekaApplication.setName(app.getName());

            List<EurekaApplicationInstance> instances = new ArrayList<>();
            for (InstanceInfo info : app.getInstances()) {
                EurekaApplicationInstance eurekaApplicationInstance = new EurekaApplicationInstance();
                eurekaApplicationInstance.setInstanceId( info.getInstanceId());
                eurekaApplicationInstance.setHomePageUrl(info.getHomePageUrl());
                eurekaApplicationInstance.setHealthCheckUrl(info.getHealthCheckUrl());
                eurekaApplicationInstance.setStatusPageUrl( info.getStatusPageUrl());
                eurekaApplicationInstance.setStatus(info.getStatus().name());
                eurekaApplicationInstance.setMetadata(info.getMetadata());
                instances.add(eurekaApplicationInstance);
            }
            eurekaApplication.setInstances( instances );
            apps.add( eurekaApplication );
        }
        return apps;
    }

    /**
     * GET  /eureka/lastn : get Eureka registrations
     */
    @PostMapping(value = "/eureka/lastn", produces = "application/json")
    public ResponseEntity<ServiceResult> lastn() {
        Map<String, List<String[]>> lastn = new HashMap<>();
        PeerAwareInstanceRegistryImpl registry = (PeerAwareInstanceRegistryImpl) getRegistry();
        List<String[]> canceledMap = registry.getLastNCanceledInstances()
            .stream().map(longStringPair -> new String[] { longStringPair.first().toString(), longStringPair.second() }).collect(
                Collectors.toList());
        lastn.put("canceled", canceledMap);
        List<String[]> registeredMap = registry.getLastNRegisteredInstances()
            .stream().map(longStringPair -> new String[] { longStringPair.first().toString(), longStringPair.second() }).collect(
                Collectors.toList());
        lastn.put("registered", registeredMap);
        return ResponseEntity.ok(new ServiceResult( lastn ));
    }

    /**
     * GET  /eureka/replicas : get Eureka replicas
     */
    @PostMapping(value = "/eureka/replicas",produces = "application/json")
    public ResponseEntity<ServiceResult> replicas() {
        List<String> replicas = new ArrayList<>();
        getServerContext().getPeerEurekaNodes().getPeerNodesView().forEach(
            node -> {
                try {
                    // The URL is parsed in order to remove login/password information
                    URI uri = new URI(node.getServiceUrl());
                    replicas.add(uri.getHost() + ":" + uri.getPort());
                } catch (URISyntaxException e) {
                    log.warn("Could not parse peer Eureka node URL: {}", e.getMessage());
                }
            }
        );

        return ResponseEntity.ok(new ServiceResult( replicas ));
    }

    /**
     * GET  /eureka/status : get Eureka status
     */
    @PostMapping(value = "/eureka/status", produces = "application/json")
    public ResponseEntity<ServiceResult> eurekaStatus() {

        EurekaStatus eurekaStatus = new EurekaStatus();
        eurekaStatus.setTime(new Date());
        eurekaStatus.setCurrentTime(StatusResource.getCurrentTimeAsString());
        eurekaStatus.setUpTime(StatusInfo.getUpTime());
        eurekaStatus.setEnvironment(ConfigurationManager.getDeploymentContext()
            .getDeploymentEnvironment());
        eurekaStatus.setDatacenter( ConfigurationManager.getDeploymentContext()
            .getDeploymentDatacenter());

        PeerAwareInstanceRegistry registry = getRegistry();
        eurekaStatus.setIsBelowRenewThreshold(registry.isBelowRenewThresold() == 1);

        StatusInfo statusInfo;
        try {
            statusInfo = new StatusResource().getStatusInfo();
        } catch (Exception e) {
            log.error(e.getMessage());
            statusInfo = StatusInfo.Builder.newBuilder().isHealthy(false).build();
        }
        if (statusInfo != null && statusInfo.getGeneralStats() != null) {
            eurekaStatus.setGeneralStats( statusInfo.getGeneralStats());
        }
        if (statusInfo != null && statusInfo.getInstanceInfo() != null) {
            InstanceInfo instanceInfo = statusInfo.getInstanceInfo();
            eurekaStatus.setInstanceInfoIpAddr(instanceInfo.getIPAddr());
            eurekaStatus.setInstanceInfoStatus(instanceInfo.getStatus().toString());
            eurekaStatus.setManagementUrl(instanceInfo.getMetadata().get("management.url"));
        }

        return ResponseEntity.ok(new ServiceResult( eurekaStatus ));
    }

    private PeerAwareInstanceRegistry getRegistry() {
        return getServerContext().getRegistry();
    }

    private EurekaServerContext getServerContext() {
        return EurekaServerContextHolder.getInstance().getServerContext();
    }

}
