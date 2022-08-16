
/*
 * © Copyright 2019 - 2020 Micro Focus or one of its affiliates.
 */

package com.ppm.integration.agilesdk.connector.snow;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.ppm.integration.agilesdk.FunctionIntegration;
import com.ppm.integration.agilesdk.IntegrationConnector;
import com.ppm.integration.agilesdk.ValueSet;
import com.ppm.integration.agilesdk.connector.snow.model.SNowProduct;
import com.ppm.integration.agilesdk.connector.snow.model.SNowUser;
import com.ppm.integration.agilesdk.connector.snow.service.SNowServiceProvider;
import com.ppm.integration.agilesdk.model.AgileProject;
import com.ppm.integration.agilesdk.ui.*;
import com.sun.jimi.core.util.P;

/**
 * Main Connector class file for ServiceNow connector.
 */
public class SNowIntegrationConnector extends IntegrationConnector {

    @Override
    public String getExternalApplicationName() {
        return "Service Now";
    }

    @Override
    public String getExternalApplicationVersionIndication() {
        return "San Diego";
    }

    @Override
    public String getConnectorVersion() {
        return "0.1";
    }

    @Override
    public String getTargetApplicationIcon() {
        return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAF7SURBVDhPpZNPKwVRGIfPDJI/K0uKBeIjUCxkoy652SjhshMLkZ10WVhJSrGwEPkEyr3JN2BjQ11s+AKSlIsynt/MOWPSJOWp577vOXPPO+ffmN9YKxxWrp0cVNhmKp6NMQzqIsxgHzbhJ95iEXfymdwDMSYuwMA6wi5Ohh3plHElMGZzNZMj2AIMriGcYY/af2Ar+KxaXB0aM77t2MCfg/W2Syxh+LYEC57/MarE4+0dxGtMbpaWssx6n9TgP52EfexW26K9aNMMpjA5+MgE/pwbLMg1iwG8DzsiWrBfBX5OfT0/OGHTbyjyTNiOWjG9KtAY5SHvnmdubJ7GlY2OJhXQZjmqgsDU2jyNehsdZRXQ+hw61uEoTSVro6OkAoUoj9lk19ttHkOfjm08aoXoaIs6Rl2iO9S1dbzgHl6glpTBEUxe/WM2Nuvz80pDd1933vGGDdiMKlyNH+h4xHklyW9hmrCEp5insGYRw/NWwhaq4CzPz9X/T4z5AqLGYpa87izgAAAAAElFTkSuQmCC";
    }

    @Override
    public List<Field> getDriverConfigurationFields() {
        return Arrays.asList(new Field[]{
                new PlainText(SNowConstants.KEY_SNOW_URL, "SNOW_URL", "", true),
                new PlainText(SNowConstants.KEY_PROXY_HOST, "PROXY_HOST", "", false),
                new PlainText(SNowConstants.KEY_PROXY_PORT, "PROXY_PORT", "", false),
                new LineBreaker(),
                new LabelText("", "AUTHENTICATION_SETTINGS_SECTION", "block", false),
                new PlainText(SNowConstants.KEY_USERNAME, "USERNAME", "", true),
                new PasswordText(SNowConstants.KEY_PASSWORD, "PASSWORD", "", true),
        });
    }

    @Override
    public List<AgileProject> getAgileProjects(ValueSet instanceConfigurationParameters) {

        AgileProject snowProject = new AgileProject();
        snowProject.setDisplayName("SNow Instance");
        snowProject.setValue("SNow Instance");

        return Arrays.asList(snowProject);

    }

    @Override
    public List<FunctionIntegration> getIntegrations() {
        return Arrays.asList(new FunctionIntegration[]{new SNowWorkPlanIntegration()});
    }

    @Override
    public List<String> getIntegrationClasses() {
        return Arrays.asList(new String[]{"com.ppm.integration.agilesdk.connector.snow.SNowWorkPlanIntegration", "com.ppm.integration.agilesdk.connector.snow.SNowRequestIntegration"});
    }

    @Override
    public String testConnection(ValueSet instanceConfigurationParameters) {
        String username = instanceConfigurationParameters.get(SNowConstants.KEY_USERNAME);
        try {
           SNowUser currentUser = SNowServiceProvider.get(instanceConfigurationParameters).getUserInfo(username);
           if (currentUser == null) {
               throw new RuntimeException("We couldn't find a valid SNow User for username "+username);
           }
        } catch (Exception e) {
            return e.getMessage();
        }

        // No problem!
        return null;
    }
}
