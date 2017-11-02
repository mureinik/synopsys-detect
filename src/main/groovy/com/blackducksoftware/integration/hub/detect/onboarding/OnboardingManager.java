/*
 * Copyright (C) 2017 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.integration.hub.detect.onboarding;

import java.io.Console;
import java.io.PrintStream;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.blackducksoftware.integration.hub.detect.DetectConfiguration;
import com.blackducksoftware.integration.hub.detect.onboarding.flow.DefaultOnboardingFlow;
import com.blackducksoftware.integration.hub.detect.profile.manager.ProfileManager;

@Component
public class OnboardingManager {

    private final Logger logger = LoggerFactory.getLogger(OnboardingManager.class);

    @Autowired
    DetectConfiguration detectConfiguration;

    @Autowired
    ProfileManager profileManager;

    @Autowired
    List<OnboardingFlow> onboardingFlows;

    @Autowired
    DefaultOnboardingFlow defaultOnboardingFlow;

    public List<OnboardingOption> onboard(final List<String> profiles) {

        Onboarder onboarder;
        final Console console = System.console();
        if (console != null) {
            onboarder = new Onboarder(new PrintStream(System.out), new ConsoleOnboardingReader(console), detectConfiguration);
        } else {
            logger.warn("Onboarding passwords may be insecure because you are running in a virtual console.");
            onboarder = new Onboarder(new PrintStream(System.out), new ScannerOnboardingReader(System.in), detectConfiguration);
        }

        onboarder.println("");
        onboarder.println("Onboarding flag found.");

        for (final String profile : profiles) {
            for (final OnboardingFlow onboardFlow : onboardingFlows) {
                final Set<String> applicable = profileManager.getProfilesFromOnboardingFlow(onboardFlow.getClass());

                if (applicable.contains(profile)) {
                    onboarder.println("Starting '" + onboardFlow.getClass().getSimpleName() + "' from profile '" + profile + "'.");
                    onboarder.println("");
                    onboard(onboardFlow, onboarder);
                    return onboarder.getOnboardedOptions();
                }
            }
        }

        onboarder.println("Starting default onboarder.");
        onboarder.println("");
        onboard(defaultOnboardingFlow, onboarder);
        return onboarder.getOnboardedOptions();
    }

    public void onboard(final OnboardingFlow onboardFlow, final Onboarder onboarder) {
        try {
            onboardFlow.onboard(onboarder);
        } catch (final Exception e) {
            logger.error(e.toString());
            logger.error("Onboarding failed. Please retry onboarding or remove '-o' and '--onboard' from your options.");
            return;
        }
    }

}