package com.ppm.integration.agilesdk.connector.snow.model;

public abstract class SNowObject {

    public String number;
    public String sys_id;

    public class Property {
        public String id;
        public String visibleValue;
    }

    public class Choice {
        public String label;
        public String value;
    }

    public class Link {
        public String link;
        public String value;
    }

    public String getNumber() {
        return number;
    }

}

