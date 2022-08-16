package com.ppm.integration.agilesdk.connector.snow.model;

import com.kintana.core.logging.LogManager;
import com.kintana.core.logging.Logger;

public class SNowUser extends SNowObject {
    private final static Logger logger = LogManager.getLogger(SNowUser.class);

    public String user_name;
    public String first_name;
    public String middle_name;
    public String last_name;
    public String email;
    public String name; // Full Name


}
