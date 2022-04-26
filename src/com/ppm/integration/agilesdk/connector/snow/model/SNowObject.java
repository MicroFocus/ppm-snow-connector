package com.ppm.integration.agilesdk.connector.snow.model;

public abstract class SNowObject {

    public String number;

    public class Property {
        public String id;
        public String visibleValue;
    }

    public String getNumber() {
        return number;
    }

}

