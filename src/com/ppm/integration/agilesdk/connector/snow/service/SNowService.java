package com.ppm.integration.agilesdk.connector.snow.service;

import com.google.gson.*;
import com.hp.ppm.common.model.AgileEntityIdName;
import com.hp.ppm.common.model.AgileEntityIdProjectDate;
import com.ppm.integration.agilesdk.connector.snow.SNowConstants;
import com.ppm.integration.agilesdk.connector.snow.SNowRequestIntegration;
import com.ppm.integration.agilesdk.connector.snow.model.*;
import com.ppm.integration.agilesdk.connector.snow.rest.SNowRestClient;
import com.ppm.integration.agilesdk.dm.DataField;
import com.ppm.integration.agilesdk.dm.ListNode;
import com.ppm.integration.agilesdk.dm.StringField;
import com.ppm.integration.agilesdk.dm.User;
import com.ppm.integration.agilesdk.model.AgileEntity;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.wink.client.ClientResponse;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Class in charge of making calls to Notion REST API when needed. Contains a cache, so the service should not be a static member of a class, as the caches are never invalidated and might contain stale data if used as such.
 * <p>
 * This class not thread safe.
 */
public class SNowService {

    private final static Logger logger = Logger.getLogger(SNowService.class);
    public static final String INTERNAL_PPM_ENTITY_URL = "internal_ppm_entity_url";

    private SNowRestClient restClient;

    private Gson gson;

    private SNowUserRetriever snowUserRetriever;

    public SNowService(SNowRestClient restClient) {
        this.restClient = restClient;
        this.snowUserRetriever = new SNowUserRetriever(this);
    }

    private List<SNowProduct> allProducts = null;

    public List<SNowProduct> getAllProducts() {

        if (allProducts == null) {
            List<SNowProduct> products = runGetTableList(SNowConstants.PRODUCTS_TABLE, SNowProduct.class, "number,description,short_description", null);
            allProducts = products;
        }

        return allProducts;
    }

    /**
     * Makes the REST call to SNow on GET for a specific table, and expects the result to be: { results: [...] }.
     * <p>
     * The results contents will be returned as a list of objects of the requested class.
     */
    private <T> List<T> runGetTableList(String tableName, Class<T> returnedObjectClass, String columnNames, String queryString) {
        String fullUrl = restClient.getSNowRestConfig().getSNowUrl() + SNowConstants.TABLE_API_ENDPOINT + tableName + "?sysparm_display_value=false";
        try {
            if (!StringUtils.isBlank(columnNames)) {
                fullUrl += "&sysparm_fields=" + URLEncoder.encode(columnNames, "UTF-8");
            }
            if (!StringUtils.isBlank(queryString)) {
                fullUrl += "&sysparm_query=" + URLEncoder.encode(queryString, "UTF-8");
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        ClientResponse response = restClient.sendGet(fullUrl);

        JsonObject result = JsonParser.parseString(response.getEntity(String.class)).getAsJsonObject();

        final List<T> results = new ArrayList<>();

        if (result.has("result") && result.get("result").isJsonArray()) {
            result.getAsJsonArray("result").forEach(new Consumer<JsonElement>() {
                @Override
                public void accept(JsonElement jsonElement) {
                    T obj = parseSnowRecord(jsonElement, returnedObjectClass, tableName);
                    results.add(obj);
                }
            });
        }

        return results;
    }

    /**
     * When we use GSon custom deserialization for AgileEntity, we need to include the URL info that
     * uses data that's not in the returned json object - so we first add the URL as a property in every
     * object when that's the case, and read from it when deserializing!
     */
    private <T> T parseSnowRecord(JsonElement jsonElement, Class<T> returnedObjectClass, String tableName) {
        if (returnedObjectClass.equals(AgileEntity.class) && !StringUtils.isBlank(tableName) && jsonElement.isJsonObject() && jsonElement.getAsJsonObject().has("sys_id")) {
            // Need to inject URL
            String sysId = jsonElement.getAsJsonObject().getAsJsonPrimitive("sys_id").getAsString();
            jsonElement.getAsJsonObject().addProperty(INTERNAL_PPM_ENTITY_URL, restClient.getSNowRestConfig().getSNowUrl() + "/" + tableName + ".do?sys_id=" + sysId);
        }

        return getGson().fromJson(jsonElement, returnedObjectClass);
    }

    private JsonObject runGetTableRecord(String tableName, String sysId, String columnNames) {
        String fullUrl = restClient.getSNowRestConfig().getSNowUrl() + SNowConstants.TABLE_API_ENDPOINT + tableName + "/" + sysId + "?sysparm_display_value=false";
        try {
            if (!StringUtils.isBlank(columnNames)) {
                fullUrl += "&sysparm_fields=" + URLEncoder.encode(columnNames, "UTF-8");
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        ClientResponse response = restClient.sendGet(fullUrl);

        JsonObject result = JsonParser.parseString(response.getEntity(String.class)).getAsJsonObject();

        if (result.has("result") && result.get("result").isJsonObject()) {
            return result.getAsJsonObject("result");
        }

        // Object not found.
        return null;
    }

    private Gson getGson() {
        if (gson == null) {
            gson = new GsonBuilder()
                    .registerTypeAdapter(SNowObject.Link.class, new SNowLinkDeserializer())
                    .registerTypeAdapter(AgileEntity.class, new AgileEntityDeserializer())
                    .registerTypeAdapter(AgileEntityIdProjectDate.class, new AgileEntityIdProjectDateDeserializer())
                    .registerTypeAdapter(AgileEntityIdName.class, new AgileEntityIdNameDeserializer())
                    .create();
        }

        return gson;
    }

    public AgileEntity createTableRecord(String tableName, AgileEntity entity) {
        String fullUrl = restClient.getSNowRestConfig().getSNowUrl() + SNowConstants.TABLE_API_ENDPOINT + tableName + "?sysparm_display_value=false";
        String jsonPayload = getSNowCreatePayloadFromAgileEntity(entity);
        ClientResponse response = restClient.sendPost(fullUrl, jsonPayload, 201);

        JsonObject createdSNow = JsonParser.parseString(response.getEntity(String.class)).getAsJsonObject();

        return parseSnowRecord(createdSNow.getAsJsonObject("result"), AgileEntity.class, tableName);
    }

    private String getSNowCreatePayloadFromAgileEntity(AgileEntity entity) {
        JsonObject payload = new JsonObject();
        for (Iterator<Map.Entry<String, DataField>> it = entity.getAllFields(); it.hasNext(); ) {
            Map.Entry<String, DataField> field = it.next();
            String fieldName = field.getKey();
            DataField dataField = field.getValue();

            if (dataField == null) {
                // SNow handles empty fields as empty string, always.
                payload.addProperty(fieldName, "");
                continue;
            }

            DataField.DATA_TYPE type = dataField.getType();
            if (type == null) {
                type = DataField.DATA_TYPE.STRING;
            }
            Object ppmValue = dataField.get();

            // We do not support multi-values in SNow for now.
            if (dataField.isList() && ppmValue != null) {
                List values = (List) ppmValue;
                if (values.isEmpty()) {
                    ppmValue = null;
                } else {
                    ppmValue = values.get(0);
                }
            }

            switch (type) {

                case USER:
                    User user = (User) ppmValue;
                    String snowUsername = getSNowUserRetriever().getSNowUsername(user);
                    payload.addProperty(fieldName, snowUsername == null ? "" : snowUsername);
                    break;
                case ListNode:
                    ListNode listNode = (ListNode) ppmValue;
                    payload.addProperty(fieldName, (listNode == null || listNode.getId() == null) ? "" : listNode.getId());
                    break;
                default: // Everything else is considered a string when generating the payload.
                    payload.addProperty(fieldName, ppmValue == null ? "" : ppmValue.toString());
                    break;
            }
        }
        return payload.toString();
    }

    public SNowUserRetriever getSNowUserRetriever() {
        return snowUserRetriever;
    }

    public String findUsernameByCriteria(String fieldName, String fieldValue) {
        if (StringUtils.isBlank(fieldName) || StringUtils.isBlank(fieldValue)) {
            return null;
        }
        List<SNowUser> users = runGetTableList("sys_user", SNowUser.class, "user_name,email,name", fieldName + "=" + fieldValue);

        // We return null if there's more than one user match.
        if (users != null && users.size() == 1 && !StringUtils.isBlank(users.get(0).user_name)) {
            return users.get(0).user_name;
        }

        return null;
    }

    public AgileEntity getTableRecord(String tableName, String entitySysId) {
        JsonObject record = runGetTableRecord(tableName, entitySysId, null);
        return parseSnowRecord(record, AgileEntity.class, tableName);
    }

    public AgileEntity updateTableRecord(String tableName, AgileEntity entity) {
        String sysId = SNowRequestIntegration.extractSysId(entity.getId());
        String fullUrl = restClient.getSNowRestConfig().getSNowUrl() + SNowConstants.TABLE_API_ENDPOINT + tableName + "/" + sysId + "?sysparm_display_value=false";
        String jsonPayload = getSNowCreatePayloadFromAgileEntity(entity);
        ClientResponse response = restClient.sendPut(fullUrl, jsonPayload, 200);

        JsonObject updatedSNow = JsonParser.parseString(response.getEntity(String.class)).getAsJsonObject();

        return parseSnowRecord(updatedSNow.getAsJsonObject("result"), AgileEntity.class, tableName);
    }

    public List<AgileEntity> getTableRecordsModifiedSince(String tableName, Set<String> entityIds, Date modifiedSinceDate) {
        String queryString = "";
        if (entityIds != null && !entityIds.isEmpty()) {
            queryString = "sys_idIN" + entityIds.stream().map(entityId -> SNowRequestIntegration.extractSysId(entityId)).collect(Collectors.joining(","));
        }
        if (modifiedSinceDate != null) {
            if (!StringUtils.isBlank(queryString)) {
                queryString += "AND";
            }
            // Example of query string for date: sys_updated_on>javascript:gs.dateGenerate('2022-06-13','14:15:16')
            // This is taken from the query string builder. There must be a cleaner way to pass dates, but this works...
            queryString += "sys_updated_on>javascript:gs.dateGenerate('" + new SimpleDateFormat("yyyy-MM-dd").format(modifiedSinceDate) + "','" + new SimpleDateFormat("HH:mm:ss").format(modifiedSinceDate) + "')";
        }
        return runGetTableList(tableName, AgileEntity.class, null, queryString);
    }

    public List<AgileEntityIdProjectDate> getAgileEntityIDsToCreateInPPM(String tableName, Date createdSinceDate) {
        String queryString = "";
        if (createdSinceDate != null) {

            // Example of query string for date: sys_updated_on>javascript:gs.dateGenerate('2022-06-13','14:15:16')
            // This is taken from the query string builder. There must be a cleaner way to pass dates, but this works...
            queryString += "sys_created_on>javascript:gs.dateGenerate('" + new SimpleDateFormat("yyyy-MM-dd").format(createdSinceDate) + "','" + new SimpleDateFormat("HH:mm:ss").format(createdSinceDate) + "')";
        }
        List<AgileEntityIdProjectDate> entitiesIdsCreationDates = runGetTableList(tableName, AgileEntityIdProjectDate.class, "sys_created_on,sys_id,number", queryString);

        return entitiesIdsCreationDates;
    }

    public List<AgileEntityIdName> getAgileEntityIDsNames(String tableName) {
        List<AgileEntityIdName> entitiesIdsCreationDates = runGetTableList(tableName, AgileEntityIdName.class, ",sys_id,number,short_description", null);
        return entitiesIdsCreationDates;
    }

    /**
     * SNow will return empty string for Links if there's no value... we need to deserialize this with special logic
     */
    private static class SNowLinkDeserializer implements JsonDeserializer<SNowObject.Link> {

        @Override
        public SNowObject.Link deserialize(JsonElement elem, Type arg1,
                                           JsonDeserializationContext arg2) throws JsonParseException {
            if (elem.isJsonObject()) {
                Gson g = new Gson();
                return g.fromJson(elem, SNowObject.Link.class);
            } else {
                // Empty String
                return null;
            }
        }
    }

    /**
     * SNow will return empty string for Links if there's no value... we need to deserialize this with special logic
     */
    private static class AgileEntityDeserializer implements JsonDeserializer<AgileEntity> {

        @Override
        public AgileEntity deserialize(JsonElement snowRecordElem, Type arg1,
                                       JsonDeserializationContext arg2) throws JsonParseException {

            if (!snowRecordElem.isJsonObject()) {
                throw new RuntimeException("Can only deserialize JSonObject into AgileEntity, but this wasn't:" + snowRecordElem.toString());
            }

            JsonObject snowRecord = snowRecordElem.getAsJsonObject();

            AgileEntity entity = new AgileEntity();

            String sysId = "ERROR";
            String number = "N/A";

            if (snowRecord.has("sys_id")) {
                sysId = snowRecord.getAsJsonPrimitive("sys_id").getAsString();
            }

            if (snowRecord.has(INTERNAL_PPM_ENTITY_URL)) {
                entity.setEntityUrl(snowRecord.getAsJsonPrimitive(INTERNAL_PPM_ENTITY_URL).getAsString());
            }

            if (snowRecord.has("number")) {
                number = snowRecord.getAsJsonPrimitive("number").getAsString();
            }

            // We include both the number and the SYS ID in the PPM Entity ID.
            // Sync ops needs the SYS_ID (unique identifier), but end-users are only familiar with Number
            String entityId = number + " (" + sysId + ")";
            entity.setId(entityId);

            if (snowRecord.has("sys_updated_on")) {
                String lastUpdateDate = snowRecord.getAsJsonPrimitive("sys_updated_on").getAsString();
                try {
                    entity.setLastUpdateTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(lastUpdateDate));
                } catch (ParseException e) {
                    throw new RuntimeException("Couldn't parse following last update date value: " + lastUpdateDate);
                }
            }

            // Obviously, this code is simplistic as we don't care about the types of returned entity
            // TODO: Check for types of field/metadata to return them more accurately.
            for (Map.Entry<String, JsonElement> prop : snowRecord.entrySet()) {
                JsonElement value = prop.getValue();
                if (value.isJsonPrimitive()) {
                    String strValue = value.getAsJsonPrimitive().getAsString();
                    DataField ppmValue = new StringField();
                    ppmValue.set(strValue);
                    entity.addField(prop.getKey(), ppmValue);
                }
            }
            return entity;
        }
    }



    /**
     * To deserialize an entity into an AgileEntityIdName, we need it to include sys_id, number and short_description.
     * The project is dummy so we'll set the SNow instance in it.
     */
    private static class AgileEntityIdNameDeserializer implements JsonDeserializer<AgileEntityIdName> {

        @Override
        public AgileEntityIdName deserialize(JsonElement snowRecordElem, Type arg1,
                                                    JsonDeserializationContext arg2) throws JsonParseException {

            if (!snowRecordElem.isJsonObject()) {
                throw new RuntimeException("Can only deserialize JSonObject into AgileEntityIdName, but this wasn't:" + snowRecordElem.toString());
            }

            JsonObject snowRecord = snowRecordElem.getAsJsonObject();


            String sysId = "ERROR";
            String number = "N/A";

            if (snowRecord.has("sys_id")) {
                sysId = snowRecord.getAsJsonPrimitive("sys_id").getAsString();
            }


            if (snowRecord.has("number")) {
                number = snowRecord.getAsJsonPrimitive("number").getAsString();
            }

            // We include both the number and the SYS ID in the PPM Entity ID.
            // Sync ops needs the SYS_ID (unique identifier), but end-users are only familiar with Number
            String entityId = number + " (" + sysId + ")";

            String description = "";

            if (snowRecord.has("short_description")) {
                description = snowRecord.getAsJsonPrimitive("short_description").getAsString();
            }
            String entityName = "[" + number + "] " + description;
            AgileEntityIdName entity = new AgileEntityIdName(entityId, entityName);

            return entity;
        }
    }

    /**
     * To deserialize an entity into an AgileEntityIdProjectDate, we need it to include sys_id, number and sys_created_on.
     * The project is dummy so we'll set the SNow instance in it.
     */
    private static class AgileEntityIdProjectDateDeserializer implements JsonDeserializer<AgileEntityIdProjectDate> {

        @Override
        public AgileEntityIdProjectDate deserialize(JsonElement snowRecordElem, Type arg1,
                                       JsonDeserializationContext arg2) throws JsonParseException {

            if (!snowRecordElem.isJsonObject()) {
                throw new RuntimeException("Can only deserialize JSonObject into AgileEntityIdProjectDate, but this wasn't:" + snowRecordElem.toString());
            }

            JsonObject snowRecord = snowRecordElem.getAsJsonObject();


            String sysId = "ERROR";
            String number = "N/A";

            if (snowRecord.has("sys_id")) {
                sysId = snowRecord.getAsJsonPrimitive("sys_id").getAsString();
            }


            if (snowRecord.has("number")) {
                number = snowRecord.getAsJsonPrimitive("number").getAsString();
            }

            // We include both the number and the SYS ID in the PPM Entity ID.
            // Sync ops needs the SYS_ID (unique identifier), but end-users are only familiar with Number
            String entityId = number + " (" + sysId + ")";

            Date creationDate = null;

            if (snowRecord.has("sys_created_on")) {
                String creationDateStr = snowRecord.getAsJsonPrimitive("sys_created_on").getAsString();
                try {
                    creationDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(creationDateStr);
                } catch (ParseException e) {
                    throw new RuntimeException("Couldn't parse following creation date value: " + creationDateStr);
                }
            }

            AgileEntityIdProjectDate entity = new AgileEntityIdProjectDate(entityId, "SNow Instance", creationDate);

            return entity;
        }
    }


    public SNowUser getUserInfo(String username) {
        List<SNowUser> users = runGetTableList(SNowConstants.SYS_USER_TABLE, SNowUser.class, "user_name,email,first_name,middle_name,last_name", "user_name=" + username);
        if (users.size() > 0) {
            return users.get(0);
        }

        return null;
    }

    public List<SNowTable> getAllTables() {
        return runGetTableList(SNowConstants.SYS_DB_OBJECT_TABLE, SNowTable.class, "create_access,read_access,name,sys_name,label,update_access,super_class", "update_access=true^read_access=true");
    }

    public List<SNowTableSchema> getAllTablesSchemas() {
        String fullUrl = restClient.getSNowRestConfig().getSNowUrl() + SNowConstants.SCHEMA_API_ENDPOINT;
        ClientResponse response = restClient.sendGet(fullUrl);

        JsonObject result = JsonParser.parseString(response.getEntity(String.class)).getAsJsonObject();

        final List<SNowTableSchema> results = new ArrayList<>();

        if (result.has("result") && result.get("result").isJsonArray()) {
            Gson gson = new Gson();
            result.getAsJsonArray("result").forEach(new Consumer<JsonElement>() {
                @Override
                public void accept(JsonElement jsonElement) {
                    SNowTableSchema obj = gson.fromJson(jsonElement, SNowTableSchema.class);
                    results.add(obj);
                }
            });
        }

        return results;
    }

    /**
     * Gets the fields from the current table, plus all the fields of any parent table.
     */
    public List<SNowField> getTableFields(String tableName) {

        if (StringUtils.isBlank(tableName)) {
            return new ArrayList<>();
        }

        String fullUrl = restClient.getSNowRestConfig().getSNowUrl() + SNowConstants.META_API_ENDPOINT + tableName;
        ClientResponse response = restClient.sendGet(fullUrl);

        JsonObject result = JsonParser.parseString(response.getEntity(String.class)).getAsJsonObject();

        final List<SNowField> results = new ArrayList<>();

        if (result.has("result") && result.get("result").isJsonObject()) {
            JsonObject jsonResult = result.getAsJsonObject("result");
            if (jsonResult.has("columns") && jsonResult.get("columns").isJsonObject()) {
                JsonObject columns = jsonResult.getAsJsonObject("columns");
                Gson gson = new Gson();
                for (Map.Entry<String, JsonElement> fieldEntry : columns.entrySet()) {
                    SNowField field = gson.fromJson(fieldEntry.getValue(), SNowField.class);
                    results.add(field);
                }
            }
        }

        return results;
    }

    private SNowTable getTableByField(String fieldName, String fieldValue) {

        if (StringUtils.isBlank(fieldName) || StringUtils.isBlank(fieldValue)) {
            return null;
        }

        List<SNowTable> tables = runGetTableList(SNowConstants.SYS_DB_OBJECT_TABLE, SNowTable.class, "create_access,read_access,name,sys_name,label,update_access,super_class", fieldName + "=" + fieldValue);

        if (tables != null && tables.size() == 1) {
            return tables.get(0);
        }
        if (tables == null || tables.isEmpty()) {
            return null;
        }

        throw new RuntimeException("Multiple tables were returned when trying to retrieve the table with field " + fieldName + "=" + fieldValue);
    }
}
