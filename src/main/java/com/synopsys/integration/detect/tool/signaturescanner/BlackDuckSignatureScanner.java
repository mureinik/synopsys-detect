/**
 * synopsys-detect
 *
 * Copyright (c) 2019 Synopsys, Inc.
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
package com.synopsys.integration.detect.tool.signaturescanner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.blackduck.codelocation.Result;
import com.synopsys.integration.blackduck.codelocation.signaturescanner.ScanBatch;
import com.synopsys.integration.blackduck.codelocation.signaturescanner.ScanBatchBuilder;
import com.synopsys.integration.blackduck.codelocation.signaturescanner.ScanBatchOutput;
import com.synopsys.integration.blackduck.codelocation.signaturescanner.ScanBatchRunner;
import com.synopsys.integration.blackduck.codelocation.signaturescanner.command.ScanCommandOutput;
import com.synopsys.integration.blackduck.codelocation.signaturescanner.command.ScanTarget;
import com.synopsys.integration.blackduck.configuration.BlackDuckServerConfig;
import com.synopsys.integration.detect.configuration.DetectProperty;
import com.synopsys.integration.detect.exception.DetectUserFriendlyException;
import com.synopsys.integration.detect.exitcode.ExitCodeType;
import com.synopsys.integration.detect.lifecycle.shutdown.ExitCodeRequest;
import com.synopsys.integration.detect.workflow.blackduck.ExclusionPatternCreator;
import com.synopsys.integration.detect.workflow.codelocation.CodeLocationNameManager;
import com.synopsys.integration.detect.workflow.event.Event;
import com.synopsys.integration.detect.workflow.event.EventSystem;
import com.synopsys.integration.detect.workflow.file.DirectoryManager;
import com.synopsys.integration.detect.workflow.status.SignatureScanStatus;
import com.synopsys.integration.detect.workflow.status.StatusType;
import com.synopsys.integration.detectable.detectable.file.FileFinder;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.util.NameVersion;

public class BlackDuckSignatureScanner {
    private final Logger logger = LoggerFactory.getLogger(BlackDuckSignatureScanner.class);

    private final DirectoryManager directoryManager;
    private final FileFinder fileFinder;
    private final CodeLocationNameManager codeLocationNameManager;
    private final BlackDuckSignatureScannerOptions signatureScannerOptions;
    private final EventSystem eventSystem;
    private final ScanBatchRunner scanJobManager;

    //When OFFLINE, this should be NULL. No other changes required for offline (in this class).
    private final BlackDuckServerConfig blackDuckServerConfig;

    public BlackDuckSignatureScanner(final DirectoryManager directoryManager, final FileFinder fileFinder, final CodeLocationNameManager codeLocationNameManager,
        final BlackDuckSignatureScannerOptions signatureScannerOptions, final EventSystem eventSystem, final ScanBatchRunner scanJobManager, final BlackDuckServerConfig blackDuckServerConfig) {
        this.directoryManager = directoryManager;
        this.fileFinder = fileFinder;
        this.codeLocationNameManager = codeLocationNameManager;
        this.signatureScannerOptions = signatureScannerOptions;
        this.eventSystem = eventSystem;
        this.scanJobManager = scanJobManager;
        this.blackDuckServerConfig = blackDuckServerConfig;
    }

    public ScanBatchOutput performScanActions(final NameVersion projectNameVersion, final File installDirectory, final File dockerTarFile) throws IntegrationException, IOException, DetectUserFriendlyException {
        final List<SignatureScanPath> signatureScanPaths = determinePathsAndExclusions(projectNameVersion, signatureScannerOptions.getMaxDepth(), dockerTarFile);

        final ScanBatchBuilder scanJobBuilder = createDefaultScanBatchBuilder(projectNameVersion, installDirectory, signatureScanPaths, dockerTarFile);
        scanJobBuilder.fromBlackDuckServerConfig(blackDuckServerConfig);//when offline, we must still call this with 'null' as a workaround for library issues, so offline scanner must be created with this set to null.
        final ScanBatch scanJob = scanJobBuilder.build();

        final List<ScanCommandOutput> scanCommandOutputs = new ArrayList<>();
        final ScanBatchOutput scanJobOutput = scanJobManager.executeScans(scanJob);
        if (scanJobOutput.getOutputs() != null) {
            for (final ScanCommandOutput scanCommandOutput : scanJobOutput.getOutputs()) {
                scanCommandOutputs.add(scanCommandOutput);
            }
        }

        reportResults(signatureScanPaths, scanCommandOutputs);

        return scanJobOutput;
    }

    //TODO: Possibly promote this to the Tool. Ideally it would return some object describing these results and the Tool translates that into detect nonsense -jp.
    private void reportResults(final List<SignatureScanPath> signatureScanPaths, final List<ScanCommandOutput> scanCommandOutputList) {
        boolean anyFailed = false;
        boolean anyExitCodeIs64 = false;
        for (final SignatureScanPath target : signatureScanPaths) {
            final Optional<ScanCommandOutput> targetOutput = scanCommandOutputList.stream()
                                                                 .filter(output -> output.getScanTarget().equals(target.getTargetPath()))
                                                                 .findFirst();

            final StatusType scanStatus;
            if (!targetOutput.isPresent()) {
                scanStatus = StatusType.FAILURE;
                logger.info(String.format("Scanning target %s was never scanned by the BlackDuck CLI.", target.getTargetPath()));
            } else {
                final ScanCommandOutput output = targetOutput.get();
                if (output.getResult() == Result.FAILURE) {
                    scanStatus = StatusType.FAILURE;

                    if (output.getException().isPresent() && output.getErrorMessage().isPresent()) {
                        logger.error(String.format("Scanning target %s failed: %s", target.getTargetPath(), output.getErrorMessage().get()));
                        logger.debug(output.getErrorMessage().get(), output.getException().get());
                    } else if (output.getErrorMessage().isPresent()) {
                        logger.error(String.format("Scanning target %s failed: %s", target.getTargetPath(), output.getErrorMessage().get()));
                    } else {
                        logger.error(String.format("Scanning target %s failed for an unknown reason.", target.getTargetPath()));
                    }

                    if (output.getScanExitCode().isPresent()) {
                        anyExitCodeIs64 = anyExitCodeIs64 || output.getScanExitCode().get() == 64;
                    }

                } else {
                    scanStatus = StatusType.SUCCESS;
                    logger.info(String.format("%s was successfully scanned by the BlackDuck CLI.", target.getTargetPath()));
                }
            }

            anyFailed = anyFailed || scanStatus == StatusType.FAILURE;
            eventSystem.publishEvent(Event.StatusSummary, new SignatureScanStatus(target.getTargetPath(), scanStatus));
        }

        if (anyFailed) {
            eventSystem.publishEvent(Event.ExitCode, new ExitCodeRequest(ExitCodeType.FAILURE_SCAN));
        }

        if (anyExitCodeIs64) {
            logger.error("");
            logger.error("Signature scanner returned 64. The most likely cause is you are using an unsupported version of Black Duck (<5.0.0).");
            logger.error("You should update your Black Duck or downgrade your version of detect.");
            logger.error("If you are using the detect scripts, you can use DETECT_LATEST_RELEASE_VERSION.");
            logger.error("");
            eventSystem.publishEvent(Event.ExitCode, new ExitCodeRequest(ExitCodeType.FAILURE_BLACKDUCK_VERSION_NOT_SUPPORTED));
        }
    }

    private List<SignatureScanPath> determinePathsAndExclusions(final NameVersion projectNameVersion, final Integer maxDepth, final File dockerTarFile) throws IntegrationException, IOException {
        final String[] providedSignatureScanPaths = signatureScannerOptions.getSignatureScannerPaths();
        final boolean userProvidedScanTargets = null != providedSignatureScanPaths && providedSignatureScanPaths.length > 0;
        final String[] providedExclusionPatterns = signatureScannerOptions.getExclusionPatterns();
        final String[] signatureScannerExclusionNamePatterns = signatureScannerOptions.getExclusionNamePatterns();

        final List<SignatureScanPath> signatureScanPaths = new ArrayList<>();
        if (null != projectNameVersion.getName() && null != projectNameVersion.getVersion() && userProvidedScanTargets) {
            for (final String path : providedSignatureScanPaths) {
                logger.info(String.format("Registering explicit scan path %s", path));
                final SignatureScanPath scanPath = createScanPath(path, maxDepth, signatureScannerExclusionNamePatterns, providedExclusionPatterns);
                signatureScanPaths.add(scanPath);
            }
        } else if (dockerTarFile != null) {
            final SignatureScanPath scanPath = createScanPath(dockerTarFile.getCanonicalPath(), maxDepth, signatureScannerExclusionNamePatterns, providedExclusionPatterns);
            signatureScanPaths.add(scanPath);
        } else {
            final String sourcePath = directoryManager.getSourceDirectory().getAbsolutePath();
            if (userProvidedScanTargets) {
                logger.warn(String.format("No Project name or version found. Skipping User provided scan targets - registering the source path %s to scan", sourcePath));
            } else {
                logger.info(String.format("No scan targets provided - registering the source path %s to scan", sourcePath));
            }
            final SignatureScanPath scanPath = createScanPath(sourcePath, maxDepth, signatureScannerExclusionNamePatterns, providedExclusionPatterns);
            signatureScanPaths.add(scanPath);
        }
        return signatureScanPaths;
    }

    private SignatureScanPath createScanPath(final String path, final Integer maxDepth, final String[] signatureScannerExclusionNamePatterns, final String[] providedExclusionPatterns) throws IntegrationException {
        try {
            final File target = new File(path);
            final String targetPath = target.getCanonicalPath();
            final ExclusionPatternCreator exclusionPatternCreator = new ExclusionPatternCreator(fileFinder, target);

            final Set<String> scanExclusionPatterns = exclusionPatternCreator.determineExclusionPatterns(maxDepth, signatureScannerExclusionNamePatterns);
            if (null != providedExclusionPatterns) {
                scanExclusionPatterns.addAll(Arrays.asList(providedExclusionPatterns));
            }
            final SignatureScanPath signatureScanPath = new SignatureScanPath();
            signatureScanPath.setTargetPath(targetPath);
            signatureScanPath.getExclusions().addAll(scanExclusionPatterns);
            return signatureScanPath;
        } catch (final IOException e) {
            throw new IntegrationException(e.getMessage(), e);
        }
    }

    protected ScanBatchBuilder createDefaultScanBatchBuilder(final NameVersion projectNameVersion, final File installDirectory, final List<SignatureScanPath> signatureScanPaths, final File dockerTarFile) throws DetectUserFriendlyException {
        final ScanBatchBuilder scanJobBuilder = new ScanBatchBuilder();
        scanJobBuilder.scanMemoryInMegabytes(signatureScannerOptions.getScanMemory());
        scanJobBuilder.installDirectory(installDirectory);
        scanJobBuilder.outputDirectory(directoryManager.getScanOutputDirectory());

        scanJobBuilder.dryRun(signatureScannerOptions.getDryRun());
        scanJobBuilder.cleanupOutput(false);

        if (signatureScannerOptions.getUploadSource() && signatureScannerOptions.getSnippetMatching() == null) {
            throw new DetectUserFriendlyException("You must enable snippet matching using " + DetectProperty.DETECT_BLACKDUCK_SIGNATURE_SCANNER_SNIPPET_MATCHING.getPropertyKey() + " in order to use upload source.",
                ExitCodeType.FAILURE_CONFIGURATION);
        }
        scanJobBuilder.uploadSource(signatureScannerOptions.getSnippetMatching(), signatureScannerOptions.getUploadSource());

        final String additionalArguments = signatureScannerOptions.getAdditionalArguments();
        scanJobBuilder.additionalScanArguments(additionalArguments);

        final String projectName = projectNameVersion.getName();
        final String projectVersionName = projectNameVersion.getVersion();
        scanJobBuilder.projectAndVersionNames(projectName, projectVersionName);

        final String sourcePath = directoryManager.getSourceDirectory().getAbsolutePath();
        final String prefix = signatureScannerOptions.getCodeLocationPrefix();
        final String suffix = signatureScannerOptions.getCodeLocationSuffix();

        String dockerTarFilename = null;
        if (dockerTarFile != null) {
            dockerTarFilename = dockerTarFile.getName();
        }
        for (final SignatureScanPath scanPath : signatureScanPaths) {
            final String codeLocationName = codeLocationNameManager.createScanCodeLocationName(sourcePath, scanPath.getTargetPath(), dockerTarFilename, projectName, projectVersionName, prefix, suffix);
            scanJobBuilder.addTarget(ScanTarget.createBasicTarget(scanPath.getTargetPath(), scanPath.getExclusions(), codeLocationName));
        }

        return scanJobBuilder;
    }

}