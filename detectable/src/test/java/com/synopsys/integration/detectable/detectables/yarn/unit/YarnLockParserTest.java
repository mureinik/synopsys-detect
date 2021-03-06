package com.synopsys.integration.detectable.detectables.yarn.unit;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.synopsys.integration.detectable.annotations.UnitTest;
import com.synopsys.integration.detectable.detectables.yarn.parse.YarnLineLevelParser;
import com.synopsys.integration.detectable.detectables.yarn.parse.YarnLock;
import com.synopsys.integration.detectable.detectables.yarn.parse.YarnLockParser;

@UnitTest
public class YarnLockParserTest {
    @Test
    void testThatYarnLockIsParsedCorrectlyToMap() {
        final List<String> yarnLockText = new ArrayList<>();
        yarnLockText.add("# THIS IS AN AUTOGENERATED FILE. DO NOT EDIT THIS FILE DIRECTLY.");
        yarnLockText.add("# yarn lockfile v1");
        yarnLockText.add("");
        yarnLockText.add("");
        yarnLockText.add("async@0.9.0:");
        yarnLockText.add("  version \"0.9.0\"");
        yarnLockText.add("  resolved \"http://nexus.fr.murex.com/nexus3/repository/npm-all/async/-/async-0.9.0.tgz#ac3613b1da9bed1b47510bb4651b8931e47146c7\"");
        yarnLockText.add("colors@1.0.3:");
        yarnLockText.add("  version \"1.0.3\"");
        yarnLockText.add("  resolved \"http://nexus.fr.murex.com/nexus3/repository/npm-all/colors/-/colors-1.0.3.tgz#0433f44d809680fdeb60ed260f1b0c262e82a40b\"");

        final YarnLockParser yarnLockParser = new YarnLockParser(new YarnLineLevelParser());
        final YarnLock yarnLock = yarnLockParser.parseYarnLock(yarnLockText);

        assertEquals("0.9.0", yarnLock.versionForFuzzyId("async@0.9.0").get());
        assertEquals("1.0.3", yarnLock.versionForFuzzyId("colors@1.0.3").get());
    }

    @Test
    void testThatYarnLockVersionsResolveAsExpected() {
        final List<String> yarnLockText = new ArrayList<>();
        yarnLockText.add("http-proxy@^1.8.1:");
        yarnLockText.add("  version \"1.16.2\"");
        yarnLockText.add("  resolved \"http://nexus.fr.murex.com/nexus3/repository/npm-all/http-proxy/-/http-proxy-1.16.2.tgz#06dff292952bf64dbe8471fa9df73066d4f37742\"");
        yarnLockText.add("  dependencies:");
        yarnLockText.add("    eventemitter3 \"1.x.x\"");
        yarnLockText.add("    requires-port \"1.x.x\"");
        yarnLockText.add("http-server@^0.9.0:");
        yarnLockText.add("  version \"0.9.0\"");
        yarnLockText.add("  resolved \"http://nexus.fr.murex.com/nexus3/repository/npm-all/http-server/-/http-server-0.9.0.tgz#8f1b06bdc733618d4dc42831c7ba1aff4e06001a\"");

        final YarnLockParser yarnLockParser = new YarnLockParser(new YarnLineLevelParser());
        final YarnLock yarnLock = yarnLockParser.parseYarnLock(yarnLockText);

        assertEquals("1.16.2", yarnLock.versionForFuzzyId("http-proxy@^1.8.1").get());
        assertEquals("0.9.0", yarnLock.versionForFuzzyId("http-server@^0.9.0").get());
    }

    @Test
    void testThatMultipleDepsPerLineCanBeHandledCorrectly() {
        final List<String> yarnLockText = new ArrayList<>();
        yarnLockText.add("debug@2, debug@2.6.9, debug@^2.2.0, debug@^2.3.3, debug@~2.6.4, debug@~2.6.6:");
        yarnLockText.add("  version \"2.6.9\"");
        yarnLockText.add("  resolved \"http://nexus/nexus3/repository/npm-all/debug/-/debug-2.6.9.tgz#5d128515df134ff327e90a4c93f4e077a536341f\"");
        yarnLockText.add("  dependencies:");
        yarnLockText.add("    ms \"2.0.0\"");

        final YarnLockParser yarnLockParser = new YarnLockParser(new YarnLineLevelParser());
        final YarnLock yarnLock = yarnLockParser.parseYarnLock(yarnLockText);

        assertEquals("2.6.9", yarnLock.versionForFuzzyId("debug@2").get());
        assertEquals("2.6.9", yarnLock.versionForFuzzyId("debug@2.6.9").get());
        assertEquals("2.6.9", yarnLock.versionForFuzzyId("debug@^2.2.0").get());
        assertEquals("2.6.9", yarnLock.versionForFuzzyId("debug@^2.3.3").get());
        assertEquals("2.6.9", yarnLock.versionForFuzzyId("debug@~2.6.4").get());
        assertEquals("2.6.9", yarnLock.versionForFuzzyId("debug@~2.6.6").get());
    }

    @Test
    void testThatDependenciesWithQuotesAreResolvedCorrectly() {
        final List<String> yarnLockText = new ArrayList<>();
        yarnLockText.add("\"cssstyle@>= 0.2.37 < 0.3.0\":");
        yarnLockText.add("  version \"0.2.37\"");
        yarnLockText.add("  resolved \"http://nexus/nexus3/repository/npm-all/cssstyle/-/cssstyle-0.2.37.tgz#541097234cb2513c83ceed3acddc27ff27987d54\"");
        yarnLockText.add("  dependencies:");
        yarnLockText.add("    cssom \"0.3.x\"");

        final YarnLockParser yarnLockParser = new YarnLockParser(new YarnLineLevelParser());
        final YarnLock yarnLock = yarnLockParser.parseYarnLock(yarnLockText);

        assertEquals("0.2.37", yarnLock.versionForFuzzyId("cssstyle@>= 0.2.37 < 0.3.0").get());
    }

}
