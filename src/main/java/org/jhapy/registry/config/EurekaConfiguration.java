package org.jhapy.registry.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.context.annotation.Configuration;

/**
 * @author Alexandre Clavaud.
 * @version 1.0
 * @since 26/09/2020
 */
@Configuration
@ConditionalOnProperty(value = "spring.cloud.kubernetes.enabled",matchIfMissing = false)
@EnableEurekaServer
@EnableEurekaClient
@EnableDiscoveryClient
public class EurekaConfiguration {

}
