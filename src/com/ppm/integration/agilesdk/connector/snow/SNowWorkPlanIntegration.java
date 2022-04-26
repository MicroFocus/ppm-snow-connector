
/*
 * © Copyright 2019 - 2020 Micro Focus or one of its affiliates.
 */

package com.ppm.integration.agilesdk.connector.snow;

import com.ppm.integration.agilesdk.ValueSet;
import com.ppm.integration.agilesdk.connector.snow.service.SNowService;
import com.ppm.integration.agilesdk.connector.snow.service.SNowServiceProvider;
import com.ppm.integration.agilesdk.pm.*;
import com.ppm.integration.agilesdk.provider.LocalizationProvider;
import com.ppm.integration.agilesdk.provider.Providers;
import com.ppm.integration.agilesdk.ui.*;
import org.apache.log4j.Logger;

import java.util.*;

public class SNowWorkPlanIntegration extends WorkPlanIntegration {


    private final Logger logger = Logger.getLogger(SNowWorkPlanIntegration.class);

    public SNowWorkPlanIntegration() {
    }

    private SNowService service;

    private synchronized SNowService getService(ValueSet config) {
        if (service == null) {
            service = SNowServiceProvider.get(config);
        }
        return service;
    }

    @Override
    public List<Field> getMappingConfigurationFields(WorkPlanIntegrationContext context, ValueSet values) {

        final LocalizationProvider lp = Providers.getLocalizationProvider(SNowIntegrationConnector.class);

        List<Field> fields = new ArrayList<>();

        SelectList productsList = new SelectList(SNowConstants.KEY_WP_PRODUCT,"WP_PRODUCT",null,true);

        productsList.addLevel(SNowConstants.KEY_WP_PRODUCT, "WP_PRODUCT");

        getService(values).getAllProducts().stream().forEach(snowProduct -> productsList.addOption(new SelectList.Option(snowProduct.getNumber(),snowProduct.short_description)));

        fields.add(productsList);

        fields.add(new LineBreaker());

        return fields;
    }




    @Override
    /**
     * This method is in Charge of retrieving all Notion DB rows and turning them into a workplan structure to be imported in PPM.
     */
    public ExternalWorkPlan getExternalWorkPlan(WorkPlanIntegrationContext context, final ValueSet values) {

        final String productNumber = values.get(SNowConstants.KEY_WP_PRODUCT);

        return new ExternalWorkPlan() {

            @Override
            public List<ExternalTask> getRootTasks() {

                ExternalTask root = new ExternalTask() {
                    @Override
                    public String getName() {
                        return "All the tasks of product "+productNumber;
                    }
                };

                List<ExternalTask> rootTasks = new ArrayList<>();

                rootTasks.add(root);

                return rootTasks;
            }
        };

    }

    /**
     * This will allow to have the information in PPM DB table PPMIC_WORKPLAN_MAPPINGS of what entity in JIRA is effectively linked to the PPM work plan task.
     * It is very useful for reporting purpose.
     *
     * @since 9.42
     */
    public LinkedTaskAgileEntityInfo getAgileEntityInfoFromMappingConfiguration(ValueSet values) {
        LinkedTaskAgileEntityInfo info = new LinkedTaskAgileEntityInfo();

        String productNumber = values.get(SNowConstants.KEY_WP_PRODUCT);

        info.setProjectId(productNumber);

        // In SNow we always import everything in a given product.

        return info;
    }


    @Override
    public boolean supportTimesheetingAgainstExternalWorkPlan() {
        return true;
    }
}
