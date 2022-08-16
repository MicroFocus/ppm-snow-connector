package com.ppm.integration.agilesdk.connector.snow.model;

import com.kintana.core.logging.LogManager;
import com.kintana.core.logging.Logger;

public class SNowTableSchema extends SNowObject {
    private final static Logger logger = LogManager.getLogger(SNowTableSchema.class);

    public boolean used;
    public boolean reference;
    public String value;
    public String label;

}
