package com.ppm.integration.agilesdk.connector.snow.service;

import com.ppm.integration.agilesdk.connector.snow.rest.SNowRestClient;
import com.ppm.integration.agilesdk.dm.User;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;

/** This class (cached in a single service instance) will try to retrieve SNow users based on PPM User info.
 * Users info will be cached when possible.
 * */
public class SNowUserRetriever {

    private Map<String, String> snowUsernamesCache = new HashMap<>();

    private SNowService service;

    public SNowUserRetriever(SNowService service) {
        this.service = service;
    }

    public String getSNowUsername(User ppmUser) {
        if (ppmUser == null) {
            return null;
        }

        String userInCache = snowUsernamesCache.get(ppmUser.getUsername());

        if (userInCache != null)  return userInCache;

        String snowUsername = lookForUserInSnow(ppmUser);

        if (!StringUtils.isBlank(ppmUser.getUsername())) {
            snowUsernamesCache.put(ppmUser.getUsername(), snowUsername);
        }

        return snowUsername;
    }

    /** This method searches for a user in SNow that matches the info of the PPM User:
     * - First check email
     * - if not, username
     * - if not, full name (hoping for only one match)
     * */
    private String lookForUserInSnow(User ppmUser) {
        if (ppmUser == null) {
            return null;
        }

        if (!StringUtils.isBlank(ppmUser.getEmail())) {
            String username = service.findUsernameByCriteria("email", ppmUser.getEmail());
            if (username != null) {
                return username;
            }
        }

        if (!StringUtils.isBlank(ppmUser.getUsername())) {
            String username = service.findUsernameByCriteria("username", ppmUser.getUsername());
            if (username != null) {
                return username;
            }
        }

        if (!StringUtils.isBlank(ppmUser.getFullName())) {
            String username = service.findUsernameByCriteria("name", ppmUser.getFullName());
            if (username != null) {
                return username;
            }
        }

        return null;
    }
}
