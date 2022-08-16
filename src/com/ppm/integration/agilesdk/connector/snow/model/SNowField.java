package com.ppm.integration.agilesdk.connector.snow.model;

import com.kintana.core.logging.LogManager;
import com.kintana.core.logging.Logger;

import java.util.List;

public class SNowField extends SNowObject {
    private final static Logger logger = LogManager.getLogger(SNowField.class);

    public String label;
    public String name;
    public String type;
    public String internal_type;
    public String reference;
    public boolean read_only;
    public boolean mandatory;
    public Integer max_length;
    public List<Choice> choices;
}
