package com.ppm.integration.agilesdk.connector.snow.service;

import com.google.gson.*;
import com.ppm.integration.agilesdk.connector.snow.SNowConstants;
import com.ppm.integration.agilesdk.connector.snow.model.*;
import com.ppm.integration.agilesdk.connector.snow.rest.SNowRestClient;
import org.apache.log4j.Logger;
import org.apache.wink.client.ClientResponse;

import java.util.*;
import java.util.function.Consumer;

/**
 * Class in charge of making calls to Notion REST API when needed. Contains a cache, so the service should not be a static member of a class, as the caches are never invalidated and might contain stale data if used as such.
 *
 * This class not thread safe.
 */
public class SNowService {

    private final static Logger logger = Logger.getLogger(SNowService.class);

    private SNowRestClient restClient;
    public SNowService(SNowRestClient restClient) {
        this.restClient = restClient;
    }

    private List<SNowProduct> allProducts = null;

    public List<SNowProduct> getAllProducts() {

        if (allProducts == null) {
            List<SNowProduct> products = runGetTableList(SNowConstants.PRODUCTS_TABLE, SNowProduct.class);
            allProducts = products;
        }

        return allProducts;
    }

    /**
     * Makes the REST call to SNow on GET for a specific table, and expects the result to be: { results: [...] }.
     *
     * The results contents will be returned as a list of objects of the requested class.
     */
    private <T extends SNowObject> List<T> runGetTableList(String tableName, Class<T> returnedObjectClass) {
        String fullUrl = restClient.getSNowRestConfig().getSNowUrl() + SNowConstants.TABLE_API_ENDPOINT + tableName + "?sysparm_display_value=false&sysparm_fields=number%2Cdescription%2Cshort_description";
        ClientResponse response = restClient.sendGet(fullUrl);

        JsonObject result = JsonParser.parseString(response.getEntity(String.class)).getAsJsonObject();

        final List<T> results = new ArrayList<>();

        if (result.has("result") && result.get("result").isJsonArray()) {
            result.getAsJsonArray("result").forEach(new Consumer<JsonElement>() {
                @Override
                public void accept(JsonElement jsonElement) {
                    T obj = new Gson().fromJson(jsonElement, returnedObjectClass);
                    results.add(obj);
                }
            });
        }

        return results;
    }

}
