/**
 * detect-common
 *
 * Copyright (C) 2018 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.blackducksoftware.integration.hub.detect.help;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.blackducksoftware.integration.hub.detect.help.html.HelpHtmlOption;
import com.blackducksoftware.integration.hub.detect.help.print.HelpTextWriter;

public abstract class DetectOption {
    private final String key;
    private final String fieldName;
    private final Class<?> valueType;
    private final boolean strictAcceptableValues;
    private final boolean caseSensitiveAcceptableValues;
    private final List<String> acceptableValues;
    private final DetectOptionHelp detectOptionHelp;
    private final String originalValue;
    private final String defaultValue;
    private final String resolvedValue;
    private List<String> warnings = new ArrayList<>();
    private boolean requestedDeprecation = false;
    private String interactiveValue = null;
    private String finalValue = null;
    private FinalValueType finalValueType = FinalValueType.DEFAULT;

    public DetectOption(final String key, final String fieldName, final Class<?> valueType, final boolean strictAcceptableValues, final boolean caseSensitiveAcceptableValues, final List<String> acceptableValues,
            final DetectOptionHelp detectOptionHelp, final String originalValue, final String defaultValue, final String resolvedValue) {
        this.key = key;
        this.fieldName = fieldName;
        this.valueType = valueType;
        this.strictAcceptableValues = strictAcceptableValues;
        this.caseSensitiveAcceptableValues = caseSensitiveAcceptableValues;
        this.acceptableValues = acceptableValues;
        this.detectOptionHelp = detectOptionHelp;
        this.originalValue = originalValue;
        this.defaultValue = defaultValue;
        this.resolvedValue = resolvedValue;
    }

    public void requestDeprecation() {
        requestedDeprecation = true;
    }

    public void addWarning(final String description) {
        warnings.add(description);
    }

    public List<String> getWarnings() {
        return warnings.stream().collect(Collectors.toList());
    }

    public boolean isRequestedDeprecation() {
        return requestedDeprecation;
    }

    public FinalValueType getFinalValueType() {
        return finalValueType;
    }

    public void setFinalValueType(final FinalValueType finalValueType) {
        this.finalValueType = finalValueType;
    }

    public String getKey() {
        return key;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Class<?> getValueType() {
        return valueType;
    }

    public DetectOptionHelp getDetectOptionHelp() {
        return detectOptionHelp;
    }

    public boolean isStrictAcceptableValues() {
        return strictAcceptableValues;
    }

    public boolean isCaseSensitiveAcceptableValues() {
        return caseSensitiveAcceptableValues;
    }

    public List<String> getAcceptableValues() {
        return acceptableValues;
    }

    public boolean getCaseSensistiveAcceptableValues() {
        return caseSensitiveAcceptableValues;
    }

    public String getOriginalValue() {
        return originalValue;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getResolvedValue() {
        return resolvedValue;
    }

    public String getInteractiveValue() {
        return interactiveValue;
    }

    public void setInteractiveValue(final String interactiveValue) {
        this.interactiveValue = interactiveValue;
    }

    public String getFinalValue() {
        return finalValue;
    }

    public void setFinalValue(final String finalValue) {
        this.finalValue = finalValue;
    }

    public void setFinalValue(final String finalValue, final FinalValueType finalValueType) {
        setFinalValue(finalValue);
        setFinalValueType(finalValueType);
    }

    public abstract OptionValidationResult isAcceptableValue(final String value);

    public void printOption(final HelpTextWriter writer) {
        String description = getDetectOptionHelp().description;
        if (getDetectOptionHelp().isDeprecated) {
            description = "Will be removed in version " + getDetectOptionHelp().deprecationVersion + ". " + description;
        }
        if (getAcceptableValues().size() > 0) {
            description += " (" + getAcceptableValues().stream().collect(Collectors.joining("|")) + ")";
        }
        writer.printColumns("--" + getKey(), getDefaultValue(), description);
    }

    public void printDetailedOption(final HelpTextWriter writer) {
        writer.println("");
        writer.println("Detailed information for " + getKey());
        writer.println("");
        if (getDetectOptionHelp().isDeprecated) {
            writer.println("Deprecated: will be removed in version " + getDetectOptionHelp().deprecationVersion);
            writer.println("");
        }
        writer.println("Property description: " + getDetectOptionHelp().description);
        writer.println("Property default value: " + getDefaultValue());
        if (getAcceptableValues().size() > 0) {
            writer.println("Property acceptable values: " + getAcceptableValues().stream().collect(Collectors.joining(", ")));
        }
        writer.println("");

        final DetectOptionHelp help = getDetectOptionHelp();
        if (StringUtils.isNotBlank(help.detailedHelp)) {
            writer.println("Detailed help:");
            writer.println(help.detailedHelp);
            writer.println();
        }
    }

    public HelpHtmlOption createHtmlOption() {
        final String description = getDetectOptionHelp().description;
        String acceptableValues = "";
        if (getAcceptableValues().size() > 0) {
            acceptableValues = getAcceptableValues().stream().collect(Collectors.joining(", "));
        }
        String deprecationNotice = "";
        if (getDetectOptionHelp().isDeprecated) {
            deprecationNotice = "Will be removed in version " + getDetectOptionHelp().deprecationVersion + ". " + getDetectOptionHelp().deprecation;
        }
        final HelpHtmlOption htmlOption = new HelpHtmlOption(getKey(), getDefaultValue(), description, acceptableValues, getDetectOptionHelp().detailedHelp, deprecationNotice);
        return htmlOption;
    }

    public enum FinalValueType {
        DEFAULT, //the final value is the value in the default attribute
        INTERACTIVE, //the final value is from the interactive prompt
        LATEST, //the final value was resolved from latest
        CALCULATED, //the resolved value was not set and final value was set during init
        SUPPLIED, //the final value most likely came from spring
        OVERRIDE //the resolved value was set but during init a new value was set
    }

    public class OptionValidationResult {
        private final boolean isValid;
        private final String validationMessage;

        protected OptionValidationResult(final boolean isValid, final String validationMessage) {
            this.isValid = isValid;
            this.validationMessage = validationMessage;
        }

        public boolean isValid() {
            return isValid;
        }

        public String getValidationMessage() {
            return validationMessage;
        }
    }

}