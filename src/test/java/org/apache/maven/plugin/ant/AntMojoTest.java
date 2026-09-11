/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugin.ant;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.BuildException;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@MojoTest
public class AntMojoTest {

    @Test
    @InjectMojo(goal = "ant", pom = "src/test/resources/unit/ant-test/pom.xml")
    public void testDefaultProject(AntMojo mojo) throws Exception {
        invokeAntMojo(mojo, "ant-test");

        String mavenBuildXml =
                FileUtils.fileRead(new File("target/test/unit/ant-test/", AntBuildWriter.DEFAULT_MAVEN_BUILD_FILENAME));
        assertTrue(
                mavenBuildXml.contains("<available classname=\"junit.framework.Test\""),
                "junit.framework.Test check not found");
        assertFalse(
                mavenBuildXml.contains("org.junit.jupiter.api.Test"),
                "org.junit.jupiter.api.Test should not be generated for JUnit 3 project");
        assertFalse(
                mavenBuildXml.contains("<available classname=\"org.junit.Test\""),
                "org.junit.Test should not be generated for JUnit 3 project");
        assertTrue(mavenBuildXml.contains("<target name=\"-run-tests-junit\""), "junit task runner target not found");
        assertFalse(
                mavenBuildXml.contains("<target name=\"-run-tests-junitlauncher\""),
                "junitlauncher task runner should not be generated for JUnit 3 project");
        assertFalse(
                mavenBuildXml.contains("<target name=\"-run-tests-testng\""),
                "testng task runner should not be generated for JUnit 3 project");

        assertTrue(
                mavenBuildXml.contains(
                        "<attribute name=\"Automatic-Module-Name\" value=\"org.apache.maven.plugins.ant.test\"/>"),
                "custom Automatic-Module-Name attribute not found in jar manifest");
        assertTrue(
                mavenBuildXml.contains("<attribute name=\"My-Custom-Entry\" value=\"Hello-World\"/>"),
                "custom My-Custom-Entry attribute not found in jar manifest");
        assertTrue(
                mavenBuildXml.contains(
                        "<property name=\"project.build.finalName\" value=\"${maven.build.finalName}\"/>"),
                "project.build.finalName property not found");
        assertTrue(
                mavenBuildXml.contains("<property name=\"project.build.directory\" value=\"${maven.build.dir}\"/>"),
                "project.build.directory property not found");
        assertTrue(
                mavenBuildXml.contains(
                        "<property name=\"project.build.outputDirectory\" value=\"${maven.build.outputDir}\"/>"),
                "project.build.outputDirectory property not found");
        assertTrue(
                mavenBuildXml.contains("<property name=\"pom.build.finalName\" value=\"${project.build.finalName}\"/>"),
                "pom.build.finalName property not found");
    }

    @Test
    @InjectMojo(goal = "ant", pom = "src/test/resources/unit/ant-nodep-test/pom.xml")
    public void testProjectWithNoDep(AntMojo mojo) throws Exception {
        invokeAntMojo(mojo, "ant-nodep-test");
    }

    @Test
    @InjectMojo(goal = "ant", pom = "src/test/resources/unit/ant-nodep-test/pom.xml")
    public void testBuildWithoutPropertiesFile(AntMojo mojo) throws Exception {
        File antBasedir = new File("target/test/unit/ant-nodep-test/");
        mojo.execute();

        File antProperties = new File(antBasedir, AntBuildWriter.DEFAULT_MAVEN_PROPERTIES_FILENAME);
        assertTrue(antProperties.exists());
        assertTrue(antProperties.delete());
        assertFalse(antProperties.exists());

        File srcDir = new File("src/test/resources/unit/ant-nodep-test", "src");
        if (srcDir.exists()) {
            FileUtils.copyDirectoryStructure(srcDir, new File(antBasedir, "src"));
        }

        File antBuild = new File(antBasedir, AntBuildWriter.DEFAULT_BUILD_FILENAME);
        AntWrapper.invoke(antBuild);

        assertTrue(new File(antBasedir, "target/classes").exists());
        assertTrue(new File(antBasedir, "target/ant-nodep-test.jar").exists());
    }

    @Test
    @InjectMojo(goal = "ant", pom = "src/test/resources/unit/ant-multirelease-test/pom.xml")
    public void testProjectWithMultiRelease(AntMojo mojo) throws Exception {
        invokeAntMojo(mojo, "ant-multirelease-test");
    }

    @Test
    @InjectMojo(goal = "ant", pom = "src/test/resources/unit/ant-moduleinfo-test/pom.xml")
    public void testProjectWithModuleInfo(AntMojo mojo) throws Exception {
        invokeAntMojo(mojo, "ant-moduleinfo-test");
    }

    @Test
    @InjectMojo(goal = "ant", pom = "src/test/resources/unit/ant-sisu-test/pom.xml")
    public void testProjectWithSisu(AntMojo mojo) throws Exception {
        invokeAntMojo(mojo, "ant-sisu-test");
    }

    @Test
    @InjectMojo(goal = "ant", pom = "src/test/resources/unit/ant-bnd-test/pom.xml")
    public void testProjectWithBnd(AntMojo mojo) throws Exception {
        // bndwrap wraps the already-built jar in place, so it must run after <jar>, not before.
        // bnd isn't resolvable in this hermetic test repo, and there's no present/missing gating
        // anymore (see AntExtensionWriter.writeBndWrapSequence), so the build must fail naturally.
        File testPom = new File("src/test/resources/unit/ant-bnd-test");
        File antBasedir = new File("target/test/unit/ant-bnd-test/");

        FileUtils.copyFile(new File(testPom, "bnd.bnd"), new File(antBasedir, "bnd.bnd"));

        mojo.execute();

        String mavenBuildXml = FileUtils.fileRead(new File(antBasedir, AntBuildWriter.DEFAULT_MAVEN_BUILD_FILENAME));

        int jarTaskIndex = mavenBuildXml.indexOf("<jar jarfile=\"${maven.build.dir}/${maven.build.finalName}.jar\"");
        int bndwrapIndex = mavenBuildXml.indexOf("<bndwrap");
        assertTrue(jarTaskIndex >= 0, "jar task not found");
        assertTrue(bndwrapIndex >= 0, "bndwrap task not found");
        assertTrue(jarTaskIndex < bndwrapIndex, "bndwrap must run after the jar it wraps");

        assertFalse(mavenBuildXml.contains("<touch file="), "manifest file must not be touched for bnd project");
        assertFalse(
                mavenBuildXml.contains("META-INF/MANIFEST.MF"),
                "manifest file path must not be mentioned for bnd project");
        assertFalse(
                mavenBuildXml.contains("Bundle-Version:"),
                "pre-specified Bundle-Version in bnd.bnd must not be appended");

        // ant-bnd-test/bnd.bnd is a real physical file, not inline pom config: must be <copy>-ed
        // live, not snapshotted into the build via <echo>.
        assertTrue(
                mavenBuildXml.contains("<copy file=\"bnd.bnd\""), "physical bnd.bnd must be copied, not re-serialized");

        FileUtils.copyDirectoryStructure(new File(testPom, "src"), new File(antBasedir, "src"));

        try {
            AntWrapper.invoke(new File(antBasedir, AntBuildWriter.DEFAULT_BUILD_FILENAME));
            fail("expected the build to fail fast on missing bnd");
        } catch (BuildException e) {
            assertTrue(
                    e.getMessage().contains("bndwrap"), "expected a bndwrap-related failure, got: " + e.getMessage());
        }
    }

    @Test
    @InjectMojo(goal = "ant", pom = "src/test/resources/unit/ant-bnd-inline-test/pom.xml")
    public void testProjectWithBndInlineInstructions(AntMojo mojo) throws Exception {
        // no bnd.bnd file here: instructions come from the pom's <bnd> config instead, so they
        // must be echoed fresh, not copied from a file that doesn't exist.
        File testPom = new File("src/test/resources/unit/ant-bnd-inline-test");
        File antBasedir = new File("target/test/unit/ant-bnd-inline-test/");

        mojo.execute();

        String mavenBuildXml = FileUtils.fileRead(new File(antBasedir, AntBuildWriter.DEFAULT_MAVEN_BUILD_FILENAME));

        assertFalse(
                mavenBuildXml.contains("<copy file=\"bnd.bnd\""), "no bnd.bnd file exists, so it must not be copied");
        assertTrue(
                mavenBuildXml.contains("Bundle-SymbolicName: ant-bnd-inline-test"),
                "inline instructions must be echoed into bnd.bnd");
        assertTrue(
                mavenBuildXml.contains("Bundle-Version: 1.0.0.SNAPSHOT"),
                "Bundle-Version must be automatically generated and echoed into bnd.bnd");

        int jarTaskIndex = mavenBuildXml.indexOf("<jar jarfile=\"${maven.build.dir}/${maven.build.finalName}.jar\"");
        int bndwrapIndex = mavenBuildXml.indexOf("<bndwrap");
        assertTrue(jarTaskIndex >= 0, "jar task not found");
        assertTrue(bndwrapIndex >= 0, "bndwrap task not found");
        assertTrue(jarTaskIndex < bndwrapIndex, "bndwrap must run after the jar it wraps");

        FileUtils.copyDirectoryStructure(new File(testPom, "src"), new File(antBasedir, "src"));

        try {
            AntWrapper.invoke(new File(antBasedir, AntBuildWriter.DEFAULT_BUILD_FILENAME));
            fail("expected the build to fail fast on missing bnd");
        } catch (BuildException e) {
            assertTrue(
                    e.getMessage().contains("bndwrap"), "expected a bndwrap-related failure, got: " + e.getMessage());
        }
    }

    @Test
    @InjectMojo(goal = "ant", pom = "src/test/resources/unit/ant-bundle-instructions-test/pom.xml")
    public void testProjectWithBundlePluginInstructions(AntMojo mojo) throws Exception {
        // maven-bundle-plugin's <instructions> are echoed as real bnd.bnd syntax: underscore
        // prefixes become dashes (XML names can't start with '-'), and flag directives (no value)
        // get written without a trailing colon.
        File testPom = new File("src/test/resources/unit/ant-bundle-instructions-test");
        File antBasedir = new File("target/test/unit/ant-bundle-instructions-test/");

        mojo.execute();

        String mavenBuildXml = FileUtils.fileRead(new File(antBasedir, AntBuildWriter.DEFAULT_MAVEN_BUILD_FILENAME));

        assertTrue(mavenBuildXml.contains("<echo file=\"${maven.build.dir}/bnd.bnd\""), "echo bnd.bnd task not found");
        assertTrue(
                mavenBuildXml.contains("Bundle-SymbolicName: ant-bundle-instructions-test"),
                "Bundle-SymbolicName entry not found");
        assertTrue(mavenBuildXml.contains("Export-Package: *"), "Export-Package entry not found");
        assertTrue(
                mavenBuildXml.contains("-exportcontents: org.apache.maven.plugin.ant.*"),
                "-exportcontents directive not found");
        assertTrue(mavenBuildXml.contains("-noee"), "-noee directive not found");
        assertFalse(mavenBuildXml.contains("-noee:"), "-noee directive should not have colon");

        int jarTaskIndex = mavenBuildXml.indexOf("<jar jarfile=\"${maven.build.dir}/${maven.build.finalName}.jar\"");
        int bndwrapIndex = mavenBuildXml.indexOf("<bndwrap");
        assertTrue(jarTaskIndex >= 0, "jar task not found");
        assertTrue(bndwrapIndex >= 0, "bndwrap task not found");
        assertTrue(jarTaskIndex < bndwrapIndex, "bndwrap must run after the jar it wraps");

        FileUtils.copyDirectoryStructure(new File(testPom, "src"), new File(antBasedir, "src"));

        try {
            AntWrapper.invoke(new File(antBasedir, AntBuildWriter.DEFAULT_BUILD_FILENAME));
            fail("expected the build to fail fast on missing bnd");
        } catch (BuildException e) {
            assertTrue(
                    e.getMessage().contains("bndwrap"), "expected a bndwrap-related failure, got: " + e.getMessage());
        }
    }

    @Test
    @InjectMojo(goal = "ant", pom = "src/test/resources/unit/ant-javacc-test/pom.xml")
    public void testProjectWithJavacc(AntMojo mojo) throws Exception {
        // javacc isn't resolvable in this hermetic test repo, so the javacc target must fail
        // rather than silently skip generation and let compile fail confusingly later. The .jj
        // fixture must be in place before the mojo runs: getGrammarFiles() scans for it at
        // generation time, same as hasBndFile() does for bnd.bnd.
        File testPom = new File("src/test/resources/unit/ant-javacc-test");
        File antBasedir = new File("target/test/unit/ant-javacc-test/");

        FileUtils.copyDirectoryStructure(new File(testPom, "src"), new File(antBasedir, "src"));

        mojo.execute();

        try {
            AntWrapper.invoke(new File(antBasedir, AntBuildWriter.DEFAULT_BUILD_FILENAME));
            fail("expected the build to fail fast on missing javacc");
        } catch (BuildException e) {
            // no custom message anymore: javacc.jar isn't resolvable in this hermetic repo, so the
            // javacc target's own <copy> (now failonerror-enabled) fails naturally, right in the
            // first target that needs it - still early, just via Ant's own error instead of ours.
            assertTrue(
                    e.getMessage().contains("javacc-7.0.12.jar"), "expected a fail-fast error, got: " + e.getMessage());
        }
    }

    @Test
    @InjectMojo(goal = "ant", pom = "src/test/resources/unit/ant-javadoc-test/pom.xml")
    public void testProjectWithJavadoc(AntMojo mojo) throws Exception {
        invokeAntMojo(mojo, "ant-javadoc-test");
    }

    private void invokeAntMojo(AntMojo mojo, String testProject) throws Exception {
        File testPom = new File("src/test/resources/unit/" + testProject);

        // bnd.bnd must be in place before the mojo runs: AntBuildWriterUtil.hasBndFile() looks for
        // it at generation time to decide between copying the real file vs. echoing pom instructions.
        File antBasedir = new File("target/test/unit/" + testProject + "/");
        File bndFile = new File(testPom, "bnd.bnd");
        if (bndFile.exists()) {
            FileUtils.copyFile(bndFile, new File(antBasedir, "bnd.bnd"));
        }

        assertNotNull(mojo, "Mojo could not be looked up");
        mojo.execute();

        MavenProject currentProject = MojoExtension.getVariableValueFromObject(mojo, "project");

        File antBuild = new File(antBasedir, AntBuildWriter.DEFAULT_BUILD_FILENAME);
        assertTrue(antBuild.exists());
        if (!currentProject.getPackaging().toLowerCase().equals("pom")) {
            File antProperties = new File(antBasedir, AntBuildWriter.DEFAULT_MAVEN_PROPERTIES_FILENAME);
            assertTrue(antProperties.exists());
        }

        File srcDir = new File(testPom, "src");
        if (srcDir.exists()) {
            FileUtils.copyDirectoryStructure(srcDir, new File(antBasedir, "src"));
        }

        AntWrapper.invoke(antBuild);

        if (!currentProject.getPackaging().toLowerCase().equals("pom")) {
            assertTrue(new File(antBasedir, "target").exists());
            assertTrue(new File(antBasedir, "target/classes").exists());
            assertTrue(
                    new File(antBasedir, "target/" + currentProject.getBuild().getFinalName() + ".jar").exists());

            Properties properties = new Properties();
            properties.load(
                    new FileInputStream(new File(antBasedir, AntBuildWriter.DEFAULT_MAVEN_PROPERTIES_FILENAME)));
            String repo = properties.getProperty("maven.repo.local");
            assertTrue(repo.equals(new File("target/local-repo").getAbsolutePath()));
        }
    }

    @Test
    public void testHasBndKey() {
        assertFalse(AntExtensionWriter.hasBndKey(null, "Bundle-Version"));
        assertFalse(AntExtensionWriter.hasBndKey("", "Bundle-Version"));
        assertTrue(AntExtensionWriter.hasBndKey("Bundle-Version: 1.0.0", "Bundle-Version"));
        assertTrue(AntExtensionWriter.hasBndKey("Bundle-Version:1.0.0", "Bundle-Version"));
        assertTrue(AntExtensionWriter.hasBndKey("Bundle-Version = 1.0.0", "Bundle-Version"));
        assertTrue(AntExtensionWriter.hasBndKey("Bundle-Version=1.0.0", "Bundle-Version"));
        assertTrue(AntExtensionWriter.hasBndKey("  Bundle-Version : 1.0.0", "Bundle-Version"));
        assertFalse(AntExtensionWriter.hasBndKey("# Bundle-Version: 1.0.0", "Bundle-Version"));
        assertFalse(AntExtensionWriter.hasBndKey("! Bundle-Version: 1.0.0", "Bundle-Version"));
        assertFalse(
                AntExtensionWriter.hasBndKey("# Note: Bundle-Version is below\nExport-Package: *", "Bundle-Version"));
        assertFalse(AntExtensionWriter.hasBndKey("Bundle-Version-Extra: 1.0.0", "Bundle-Version"));
    }

    @Test
    @InjectMojo(goal = "ant", pom = "src/test/resources/unit/ant-modello-test/pom.xml")
    public void testProjectWithModello(AntMojo mojo) throws Exception {
        File antBasedir = new File("target/test/unit/ant-modello-test/");
        mojo.execute();

        File buildXmlFile = new File(antBasedir, AntBuildWriter.DEFAULT_MAVEN_BUILD_FILENAME);
        assertTrue(buildXmlFile.exists(), "maven-build.xml was not created");
        String mavenBuildXml = FileUtils.fileRead(buildXmlFile);

        assertTrue(
                mavenBuildXml.contains("<property name=\"maven.build.mdoDir\" value=\"src/main/mdo\"/>"),
                "maven.build.mdoDir property not found");
        assertTrue(
                mavenBuildXml.contains(
                        "<property name=\"maven.build.mdoOutputDir\" value=\"${maven.build.dir}/generated-sources/modello\"/>"),
                "maven.build.mdoOutputDir property not found");

        assertTrue(
                mavenBuildXml.contains(
                        "<target name=\"mdo\" depends=\"get-deps\" description=\"Generate sources from mdo files\">"),
                "mdo target not found");
        assertTrue(
                mavenBuildXml.contains("<typedef resource=\"com/github/fridrich/modello/ant/antlib.xml\"/>"),
                "modello typedef not found");
        assertTrue(mavenBuildXml.contains("<modello"), "modello task element not found");
        assertTrue(mavenBuildXml.contains("version=\"1.0.0\""), "modello version not found");
        assertTrue(
                mavenBuildXml.contains("outputDirectory=\"${maven.build.mdoOutputDir}\""), "outputDirectory not found");
        assertTrue(mavenBuildXml.contains("javaSource=\"8\""), "javaSource not found");
        assertFalse(mavenBuildXml.contains("domAsXpp3="), "default domAsXpp3 should not be emitted");
        assertTrue(
                mavenBuildXml.contains("<model file=\"${maven.build.mdoDir}/test.mdo\"/>"), "model element not found");
        assertTrue(mavenBuildXml.contains("<goal name=\"java\"/>"), "java goal not found");
        assertTrue(mavenBuildXml.contains("<goal name=\"xpp3-reader\"/>"), "xpp3-reader goal not found");
        assertTrue(mavenBuildXml.contains("<goal name=\"xpp3-writer\"/>"), "xpp3-writer goal not found");
        assertFalse(mavenBuildXml.contains("<goal name=\"xdoc\"/>"), "site phase xdoc goal should not be present");

        assertTrue(
                mavenBuildXml.contains("<target name=\"compile\" depends=\"mdo\""),
                "compile target should depend on mdo");
        assertTrue(
                mavenBuildXml.contains("<target name=\"javadoc\" depends=\"mdo\""),
                "javadoc target should depend on mdo");
        assertTrue(
                mavenBuildXml.contains("<pathelement location=\"${maven.build.mdoOutputDir}\"/>"),
                "mdoOutputDir not added to compile pathelement");

        File propertiesFile = new File(antBasedir, AntBuildWriter.DEFAULT_MAVEN_PROPERTIES_FILENAME);
        assertTrue(propertiesFile.exists(), "maven-build.properties was not created");
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(propertiesFile)) {
            properties.load(fis);
            assertTrue("src/main/mdo".equals(properties.getProperty("maven.build.mdoDir")));
            assertTrue("${maven.build.dir}/generated-sources/modello"
                    .equals(properties.getProperty("maven.build.mdoOutputDir")));
        }
    }
}
