package com.ppm.integration.agilesdk.connector.snow;

import com.hp.ppm.integration.model.AgileEntityFieldValue;
import com.ppm.integration.agilesdk.ValueSet;
import com.ppm.integration.agilesdk.connector.snow.model.SNowField;
import com.ppm.integration.agilesdk.connector.snow.model.SNowObject;
import com.ppm.integration.agilesdk.connector.snow.model.SNowTableSchema;
import com.ppm.integration.agilesdk.connector.snow.service.SNowServiceProvider;
import com.ppm.integration.agilesdk.dm.DataField;
import com.ppm.integration.agilesdk.dm.RequestIntegration;
import com.ppm.integration.agilesdk.model.AgileEntity;
import com.ppm.integration.agilesdk.model.AgileEntityFieldInfo;
import com.ppm.integration.agilesdk.model.AgileEntityInfo;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SNowRequestIntegration extends RequestIntegration {

    /**
     * Agile Entities are SNow Tables. We'll return all the Tables for which current user has at least view & update access.
     */
    @Override
    public List<AgileEntityInfo> getAgileEntitiesInfo(String agileProjectValue, ValueSet instanceConfigurationParameters) {
        List<SNowTableSchema> tables = SNowServiceProvider.get(instanceConfigurationParameters).getAllTablesSchemas();

        return tables.stream().map(sNowTable -> {
            AgileEntityInfo entity = new AgileEntityInfo();
            entity.setName(sNowTable.label + " (" + sNowTable.value + ")");
            entity.setType(sNowTable.value); // Name is actually the SNow identifier.
            return entity;
        }).sorted((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName())).collect(Collectors.toList());

    }

    @Override
    public List<AgileEntityFieldInfo> getAgileEntityFieldsInfo(String agileProjectValue, String entityType, ValueSet instanceConfigurationParameters) {
        // We ignore the agileProjectValue - SNow doesn't have any.
        List<SNowField> fields = SNowServiceProvider.get(instanceConfigurationParameters).getTableFields(entityType);

        return fields.stream().filter(sNowField -> {
            // We only keep "Users" reference fields.
            if (!StringUtils.isBlank(sNowField.reference) && !"sys_user".equals(sNowField.reference)) {
                return false;
            }
            return true;
        }).sorted((o1, o2) -> o1.label.compareToIgnoreCase(o2.label)).map(snowField -> {
            AgileEntityFieldInfo field = new AgileEntityFieldInfo();
            field.setFieldType(getAgileFieldtype(snowField.internal_type));
            field.setMultiValue(false); // Haven't found the property telling if a field is multi-select.
            field.setListType("choice".equals(snowField.type));
            field.setId(snowField.name);
            field.setLabel((snowField.mandatory ? "(*) " : "") + snowField.label);
            return field;
        }).collect(Collectors.toList());
    }

    private String getAgileFieldtype(String internal_type) {
        if (StringUtils.isBlank(internal_type)) {
            return DataField.DATA_TYPE.STRING.name();
        }

        switch (internal_type) {
            case "string":
            case "string_full_utf8":
            case "datetime":
            case "email":
                return DataField.DATA_TYPE.STRING.name();
            case "integer":
            case "longint":
            case "int":
            case "long":
                return DataField.DATA_TYPE.INTEGER.name();
            case "reference": // Only sys_user references fields have been kept.
                return DataField.DATA_TYPE.USER.name();
            case "float":
            case "decimal":
                return DataField.DATA_TYPE.FLOAT.name();
            default:
                return DataField.DATA_TYPE.STRING.name();
        }
    }

    @Override
    public List<AgileEntityFieldValue> getAgileEntityFieldsValueList(final String agileProjectValue,
                                                                     final String entityType, final ValueSet instanceConfigurationParameters, final String fieldName, final boolean isLogicalName) {

        List<AgileEntityFieldValue> values = new ArrayList<>();

        if (StringUtils.isBlank(entityType) || StringUtils.isBlank(fieldName)) {
            return values;
        }

        // We'll retrieve all the fields with the Meta api as it doesn't need to get admin account
        List<SNowField> fields = SNowServiceProvider.get(instanceConfigurationParameters).getTableFields(entityType);

        for (SNowField field : fields) {
            if (fieldName.equals(field.name)) {
                if (field.choices != null) {
                    for (SNowObject.Choice choice : field.choices) {
                        AgileEntityFieldValue value = new AgileEntityFieldValue();
                        value.setId(choice.value);
                        value.setName(choice.label);
                        values.add(value);
                    }
                }
            }
        }

        return values;
    }

    @Override
    public AgileEntity updateEntity(String agileProjectValue, String entityType, AgileEntity entity, ValueSet instanceConfigurationParameters) {
        AgileEntity updatedEntity = SNowServiceProvider.get(instanceConfigurationParameters).updateTableRecord(entityType, entity);
        return updatedEntity;
    }

    @Override
    public AgileEntity createEntity(String agileProjectValue, String entityType, AgileEntity entity, ValueSet instanceConfigurationParameters) {
        AgileEntity createdEntity = SNowServiceProvider.get(instanceConfigurationParameters).createTableRecord(entityType, entity);
        return createdEntity;
    }

    @Override
    public List<AgileEntity> getEntities(String agileProjectValue, String entityType, ValueSet instanceConfigurationParameters, Set<String> entityIds, Date modifiedSinceDate) {
        List<AgileEntity> entities = SNowServiceProvider.get(instanceConfigurationParameters).getTableRecordsModifiedSince(entityType, entityIds, modifiedSinceDate);
        return entities;
    }

    @Override
    public AgileEntity getEntity(String agileProjectValue, String entityType, ValueSet instanceConfigurationParameters, String entityId) {
        String sysId = extractSysId(entityId);
        return SNowServiceProvider.get(instanceConfigurationParameters).getTableRecord(entityType, sysId);
    }

    /**
     * We store entity ID under the form: "number (sys_id)".
     * We must extract sys_id as it's the only useful identifier for the API calls.
     *
     * @throws RuntimeException if we can't extract the sys_id.
     */
    public static String extractSysId(String entityId) {
        if (!StringUtils.isBlank(entityId) & entityId.contains("(") && entityId.endsWith(")")) {
            return entityId.substring(entityId.indexOf("(") + 1, entityId.length() - 1);
        }

        throw new RuntimeException("We couldn't extract the sys_id from entityId " + entityId + " . Format should be: 'number (sys_id)'");
    }
}
