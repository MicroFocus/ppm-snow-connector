
/*
 * © Copyright 2019 - 2020 Micro Focus or one of its affiliates.
 */

package com.ppm.integration.agilesdk.connector.snow.rest;


import com.kintana.core.logging.LogLevel;
import com.kintana.core.logging.LogManager;
import com.kintana.core.logging.Logger;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.Collections;
import java.util.UUID;

public class SNowRestClient {

    private boolean ENABLE_REST_CALLS_STATUS_LOG = false;

    private final static Logger logger = LogManager.getLogger(SNowRestClient.class);

    private SNowRestConfig snowConfig;
    private RestTemplate restTemplate;

    public SNowRestClient(SNowRestConfig notionConfig) {
        this.snowConfig = notionConfig;
        this.restTemplate = buildRestTemplate(notionConfig);
    }

    private URI normalizeUri(String fullUrl) {
        try {
            URL url = new URL(fullUrl);
            String urlPath = url.getHost();
            if (url.getPort() > 0) {
                urlPath = urlPath + ":" + url.getPort();
            }
            try {
                return new URI(url.getProtocol(), urlPath, url.getPath(), url.getQuery() == null ? null : URLDecoder.decode(url.getQuery(), "UTF-8"), null);
            } catch (UnsupportedEncodingException e) {
                // This will never happen.
                throw new RuntimeException("Impossible encoding error occurred", e);
            }
        } catch (MalformedURLException e) {
            throw new RestRequestException( // is a malformed URL
                    400, String.format("%s is a malformed URL", fullUrl));
        } catch (URISyntaxException e) {
            throw new RestRequestException(400, String.format("%s is a malformed URL", fullUrl));
        }
    }

    private HttpHeaders getHeaders(boolean includeContentTypeHeader, String uuid) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", snowConfig.getBasicAuthorizationToken());

        // Following header is required for easy HTTP request tracing in systems such as IBM DataPower.
        if (uuid != null) {
            headers.set("X-B3-TraceId", uuid);
        }

        if (includeContentTypeHeader) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        return headers;
    }

    private RestTemplate buildRestTemplate(SNowRestConfig config) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        if (!StringUtils.isBlank(config.getProxyHost()) && config.getProxyPort() != null) {
            requestFactory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(config.getProxyHost(), config.getProxyPort().intValue())));
        }

        RestTemplate template = new RestTemplate(requestFactory);
        template.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return template;
    }

    public ClientResponse sendGet(String uri) {

        if (ENABLE_REST_CALLS_STATUS_LOG) {
            logger.log(LogLevel.STATUS, "GET "+uri);
        }

        String uuid = UUID.randomUUID().toString();
        HttpEntity<String> requestEntity = new HttpEntity<String>(null, getHeaders(false, uuid));
        ResponseEntity<String> responseEntity = restTemplate.exchange(normalizeUri(uri), HttpMethod.GET, requestEntity, String.class);
        ClientResponse response = new ClientResponse(responseEntity.getStatusCode().value(), jsonIncludeNonNull(responseEntity.getBody()));

        checkResponseStatus(200, response, uri, "GET", null, uuid);

        return response;
    }

    private String jsonIncludeNonNull(String responseBody) {
        try {
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

                Object parsedBody = mapper.readValue(responseBody, Object.class);
                return mapper.writeValueAsString(parsedBody);
            }
        } catch (Exception e) {
            logger.debug("Error on Parsing data to remove null values from response body: " + e.getMessage(), e);
        }
        return responseBody;
    }

    private void checkResponseStatus(int expectedHttpStatusCode, ClientResponse response, String uri, String verb, String payload, String uuid) {

        if (response.getStatusCode() != expectedHttpStatusCode) {
            StringBuilder errorMessage = new StringBuilder(String.format("## Unexpected HTTP response status code %s for %s uri %s, expected %s", response.getStatusCode(), verb,  uri, expectedHttpStatusCode));
            if (uuid != null) {
                errorMessage.append(System.lineSeparator()).append("Value of HTTP tracking header X-B3-TraceId:").append(uuid);
            }
            if (payload != null) {
                errorMessage.append(System.lineSeparator()).append(System.lineSeparator()).append("# Sent Payload:").append(System.lineSeparator()).append(payload);
            }
            String responseStr = null;
            try {
                responseStr = response.getEntity(String.class);
            } catch (Exception e) {
                // we don't do anything if we cannot get the response.
            }
            if (!StringUtils.isBlank(responseStr)) {
                errorMessage.append(System.lineSeparator()).append(System.lineSeparator()).append("# Received Response:").append(System.lineSeparator()).append(responseStr);
            }

            throw new RestRequestException(response.getStatusCode(), errorMessage.toString());
        }

    }

    public ClientResponse sendPost(String uri, String jsonPayload, int expectedHttpStatusCode) {

        if (ENABLE_REST_CALLS_STATUS_LOG) {
            logger.log(LogLevel.STATUS, "POST "+uri);
        }

        String uuid = UUID.randomUUID().toString();
        HttpEntity<String> requestEntity = new HttpEntity<String>(jsonPayload, getHeaders(true, uuid));
        ResponseEntity<String> responseEntity = restTemplate.exchange(normalizeUri(uri), HttpMethod.POST, requestEntity, String.class);
        ClientResponse response = new ClientResponse(responseEntity.getStatusCode().value(), jsonIncludeNonNull(responseEntity.getBody()));
        checkResponseStatus(expectedHttpStatusCode, response, uri, "POST", jsonPayload, uuid);

        return response;
    }

    public ClientResponse sendPut(String uri, String jsonPayload, int expectedHttpStatusCode) {

        if (ENABLE_REST_CALLS_STATUS_LOG) {
            logger.log(LogLevel.STATUS, "PUT "+uri);
        }

        String uuid = UUID.randomUUID().toString();
        HttpEntity<String> requestEntity = new HttpEntity<String>(jsonPayload, getHeaders(true, uuid));
        ResponseEntity<String> responseEntity = restTemplate.exchange(normalizeUri(uri), HttpMethod.PUT, requestEntity, String.class);
        ClientResponse response = new ClientResponse(responseEntity.getStatusCode().value(), jsonIncludeNonNull(responseEntity.getBody()));

        checkResponseStatus(expectedHttpStatusCode, response, uri, "PUT", jsonPayload, uuid);

        return response;
    }


    public SNowRestConfig getSNowRestConfig() {
        return snowConfig;
    }
}
