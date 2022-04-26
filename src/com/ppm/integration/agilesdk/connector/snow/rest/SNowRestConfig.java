
/*
 * © Copyright 2019 - 2020 Micro Focus or one of its affiliates.
 */

package com.ppm.integration.agilesdk.connector.snow.rest;

import org.apache.commons.codec.binary.Base64;
import org.apache.wink.client.ClientConfig;
import org.springframework.util.StringUtils;

public class SNowRestConfig {

    private static final String BASIC_AUTHENTICATION_PREFIX = "Basic ";

    private ClientConfig clientConfig;

    private String basicAuthenticationToken;

    private String snowUrl;

    public ClientConfig getClientConfig() {
        return clientConfig;
    }

    public SNowRestConfig() {
        clientConfig = new ClientConfig();
    }


    public ClientConfig setProxy(String proxyHost, String proxyPort) {

        if (proxyHost != null && !proxyHost.isEmpty() && proxyPort != null && !proxyPort.isEmpty()) {
            clientConfig.proxyHost(proxyHost);
            clientConfig.proxyPort(Integer.parseInt(proxyPort));
        }
        return clientConfig;
    }


    public String getBasicAuthorizationToken() {
        return basicAuthenticationToken;
    }

    public void setBasicAuthorizationCredentials(String username, String password) {
        String basicToken = new String(Base64.encodeBase64((username + ":" + password).getBytes()));
        basicAuthenticationToken = BASIC_AUTHENTICATION_PREFIX + basicToken;
    }

    public String getSNowUrl() {
        return snowUrl;
    }

    public void setSNowUrl(String snowUrl) {
        if (snowUrl != null) {
            snowUrl = StringUtils.trimTrailingCharacter(snowUrl, '/');
            snowUrl = snowUrl.toLowerCase();

            if (!snowUrl.endsWith(".service-now.com")) {
                throw new RuntimeException("The ServiceNow URL should end with '.service-now.com'");
            }
        }

        this.snowUrl = snowUrl;
    }
}
