package com.ppm.integration.agilesdk.connector.snow.rest;

public class ClientResponse {

    private final int statusCode;
    private final String entity;

    public ClientResponse(int statusCode, String entity) {
        this.statusCode = statusCode;
        this.entity = entity;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public <T> T getEntity(Class<T> entityType) {
        if (entityType == String.class) {
            return entityType.cast(entity);
        }
        throw new IllegalArgumentException("Unsupported entity type: " + entityType);
    }
}

