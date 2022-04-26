/*
 * © Copyright 2019 - 2020 Micro Focus or one of its affiliates.
 */

package com.ppm.integration.agilesdk.connector.snow.service;

import com.ppm.integration.agilesdk.ValueSet;
import com.ppm.integration.agilesdk.connector.snow.SNowConstants;
import com.ppm.integration.agilesdk.connector.snow.SNowIntegrationConnector;
import com.ppm.integration.agilesdk.connector.snow.rest.SNowRestClient;
import com.ppm.integration.agilesdk.connector.snow.rest.SNowRestConfig;
import com.ppm.integration.agilesdk.provider.Providers;
import com.ppm.integration.agilesdk.provider.UserProvider;
import org.apache.commons.lang.StringUtils;

public class SNowServiceProvider {

    public static UserProvider getUserProvider() {
        return Providers.getUserProvider(SNowIntegrationConnector.class);
    }

    public static SNowService get(ValueSet config) {

        String proxyHost = config.get(SNowConstants.KEY_PROXY_HOST);

        SNowRestConfig restConfig = new SNowRestConfig();

        if (!StringUtils.isBlank(proxyHost)) {
            String proxyPort = config.get(SNowConstants.KEY_PROXY_PORT);
            if (StringUtils.isBlank(proxyPort)) {
                proxyPort = "80";
            }

            restConfig.setProxy(proxyHost, proxyPort);
        }
        restConfig.setBasicAuthorizationCredentials(config.get(SNowConstants.KEY_USERNAME), config.get(SNowConstants.KEY_PASSWORD));

        restConfig.setSNowUrl(config.get(SNowConstants.KEY_SNOW_URL));

        return new SNowService(new SNowRestClient(restConfig));
    }

}
