package com.ppm.integration.agilesdk.connector.snow.model;

import com.kintana.core.logging.LogManager;
import com.kintana.core.logging.Logger;

public class SNowTable extends SNowObject {
    private final static Logger logger = LogManager.getLogger(SNowTable.class);

    public boolean create_access;
    public boolean read_access;
    public boolean update_access;
    public String name;
    public String sys_name;
    public String label;
    public Link super_class; // Only for the tables that inherit another table. Note that super_class.value is the sys_id, not the name.

}
