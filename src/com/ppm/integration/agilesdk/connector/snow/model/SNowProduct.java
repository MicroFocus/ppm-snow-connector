package com.ppm.integration.agilesdk.connector.snow.model;

import com.hp.ppm.user.model.User;
import com.kintana.core.logging.LogManager;
import com.kintana.core.logging.Logger;
import com.ppm.integration.agilesdk.provider.UserProvider;
import org.apache.commons.lang.StringUtils;

import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.stream.Collectors;

public class SNowProduct extends SNowObject {
    private final static Logger logger = LogManager.getLogger(SNowProduct.class);

    public String short_description;
    public String description;

}
