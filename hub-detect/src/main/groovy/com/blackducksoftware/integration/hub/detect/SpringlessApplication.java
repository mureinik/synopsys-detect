/**
 * hub-detect
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
package com.blackducksoftware.integration.hub.detect;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import com.blackducksoftware.integration.hub.detect.bomtool.BomToolGroupType;
import com.blackducksoftware.integration.hub.detect.configuration.ConfigurationManager;
import com.blackducksoftware.integration.hub.detect.configuration.DetectConfiguration;
import com.blackducksoftware.integration.hub.detect.configuration.DetectProperty;
import com.blackducksoftware.integration.hub.detect.configuration.DetectPropertySource;
import com.blackducksoftware.integration.hub.detect.exception.DetectUserFriendlyException;
import com.blackducksoftware.integration.hub.detect.exitcode.ExitCodeReporter;
import com.blackducksoftware.integration.hub.detect.exitcode.ExitCodeType;
import com.blackducksoftware.integration.hub.detect.help.DetectArgumentState;
import com.blackducksoftware.integration.hub.detect.help.DetectArgumentStateParser;
import com.blackducksoftware.integration.hub.detect.help.DetectOption;
import com.blackducksoftware.integration.hub.detect.help.DetectOption.OptionValidationResult;
import com.blackducksoftware.integration.hub.detect.help.DetectOptionManager;
import com.blackducksoftware.integration.hub.detect.help.html.HelpHtmlWriter;
import com.blackducksoftware.integration.hub.detect.help.print.HelpPrinter;
import com.blackducksoftware.integration.hub.detect.hub.HubServiceManager;
import com.blackducksoftware.integration.hub.detect.interactive.InteractiveManager;
import com.blackducksoftware.integration.hub.detect.util.DetectFileManager;
import com.blackducksoftware.integration.hub.detect.workflow.DetectProjectManager;
import com.blackducksoftware.integration.hub.detect.workflow.PhoneHomeManager;
import com.blackducksoftware.integration.hub.detect.workflow.bomtool.BomToolManager;
import com.blackducksoftware.integration.hub.detect.workflow.bomtool.BomToolResult;
import com.blackducksoftware.integration.hub.detect.workflow.boot.BootManager;
import com.blackducksoftware.integration.hub.detect.workflow.boot.BootResult;
import com.blackducksoftware.integration.hub.detect.workflow.boot.DetectContext;
import com.blackducksoftware.integration.hub.detect.workflow.codelocation.DetectCodeLocationManager;
import com.blackducksoftware.integration.hub.detect.workflow.codelocation.DetectCodeLocationResult;
import com.blackducksoftware.integration.hub.detect.workflow.diagnostic.DetectRunManager;
import com.blackducksoftware.integration.hub.detect.workflow.diagnostic.DiagnosticManager;
import com.blackducksoftware.integration.hub.detect.workflow.hub.HubManager;
import com.blackducksoftware.integration.hub.detect.workflow.project.BdioManager;
import com.blackducksoftware.integration.hub.detect.workflow.project.DetectProject;
import com.blackducksoftware.integration.hub.detect.workflow.summary.DetectSummaryManager;
import com.synopsys.integration.blackduck.api.generated.view.ProjectVersionView;
import com.synopsys.integration.blackduck.summary.Result;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.log.SilentLogger;
import com.synopsys.integration.log.Slf4jIntLogger;

@SpringBootApplication
//@Import({ BeanConfiguration.class })
public class SpringlessApplication implements ApplicationRunner {
    private final Logger logger = LoggerFactory.getLogger(SpringlessApplication.class);

    private ConfigurableEnvironment environment;

    @Autowired
    public SpringlessApplication(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    public static void main(final String[] args) {
        new SpringApplicationBuilder(SpringlessApplication.class).logStartupInfo(false).run(args);
    }

    @Override
    public void run(final ApplicationArguments applicationArguments) throws Exception {
        final long startTime = System.currentTimeMillis();

        ExitCodeType detectExitCode = ExitCodeType.SUCCESS;
        try {
            AnnotationConfigApplicationContext bootContext = new AnnotationConfigApplicationContext(DetectSharedBeanConfiguration.class, DetectBootBeanConfiguration.class);
            BootManager bootManager = bootContext.getBean(BootManager.class);
            BootResult bootResult = bootManager.boot(applicationArguments.getSourceArgs(), environment);
            if (bootResult.bootType == BootResult.BootType.CONTINUE){
                runDetect(bootContext);
            }
        } catch (final Exception e) {
            //detectExitCode = getExitCodeFromExceptionDetails(e);
        } finally {
            //cleanupRun(detectExitCode);
        }

        //endRun(startTime, detectExitCode);
    }

    private ExitCodeType runDetect(AnnotationConfigApplicationContext bootContext) {

        if (!context.detectConfiguration.getBooleanProperty(DetectProperty.DETECT_BOM_TOOLS_DISABLED)){
            BomToolResult result = manager.runBomTools();

            Map<BomToolGroupType, Result> bomToolResults = new HashMap<>();
            result.succesfullBomToolGroupTypes.forEach(it -> bomToolResults.put(it, Result.SUCCESS));
            result.failedBomToolGroupTypes.forEach(it -> bomToolResults.put(it, Result.FAILURE));
        }



        AnnotationConfigApplicationContext runContext = new AnnotationConfigApplicationContext(DetectSharedBeanConfiguration.class, DetectBootBeanConfiguration.class);

        applicationContext.getBeanFactory().registerSingleton("", "");

        if (detectConfiguration.getBooleanProperty(DetectProperty.BLACKDUCK_OFFLINE_MODE)) {
            for (final File bdio : detectProject.getBdioFiles()) {
                diagnosticManager.registerGlobalFileOfInterest(bdio);
            }
        } else {
            final Optional<ProjectVersionView> originalProjectVersionView = hubManager.updateHubProjectVersion(detectProject);
            ProjectVersionView projectVersionView = null;
            if (originalProjectVersionView.isPresent()) {
                projectVersionView = originalProjectVersionView.get();
            }
            hubManager.performScanActions(detectProject);
            hubManager.performBinaryScanActions(detectProject);
            // final ProjectVersionView projectVersionView = originalProjectVersionView.orElse(scanProjectVersionView.orElse(null));
            hubManager.performPostHubActions(detectProject, projectVersionView);
        }

        if (result.failedBomToolGroupTypes.size() > 0){
            return ExitCodeType.FAILURE_BOM_TOOL;
        }
        if (result.missingBomToolGroupTypes.size() > 0){
            return ExitCodeType.FAILURE_BOM_TOOL_REQUIRED;
        }
    }

}
