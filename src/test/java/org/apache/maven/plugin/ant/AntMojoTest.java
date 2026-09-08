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

import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.BuildException;
import org.codehaus.plexus.util.FileUtils;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AntMojoTest {

    @Rule
    public MojoRule rule = new MojoRule();

    @Test
    public void testDefaultProject() throws Exception {
        invokeAntMojo("ant-test");

        String mavenBuildXml =
                FileUtils.fileRead(new File("target/test/unit/ant-test/", AntBuildWriter.DEFAULT_MAVEN_BUILD_FILENAME));
        assertTrue(
                "junit.framework.Test check not found",
                mavenBuildXml.contains("<available classname=\"junit.framework.Test\""));
        assertFalse(
                "org.junit.jupiter.api.Test should not be generated for JUnit 3 project",
                mavenBuildXml.contains("org.junit.jupiter.api.Test"));
        assertFalse(
                "org.junit.Test should not be generated for JUnit 3 project",
                mavenBuildXml.contains("<available classname=\"org.junit.Test\""));
        assertTrue("junit task runner target not found", mavenBuildXml.contains("<target name=\"-run-tests-junit\""));
        assertFalse(
                "junitlauncher task runner should not be generated for JUnit 3 project",
                mavenBuildXml.contains("<target name=\"-run-tests-junitlauncher\""));
        assertFalse(
                "testng task runner should not be generated for JUnit 3 project",
                mavenBuildXml.contains("<target name=\"-run-tests-testng\""));
    }

    @Test
    public void testProjectWithNoDep() throws Exception {
        invokeAntMojo("ant-nodep-test");
    }

    @Test
    public void testProjectWithMultiRelease() throws Exception {
        invokeAntMojo("ant-multirelease-test");
    }

    @Test
    public void testProjectWithModuleInfo() throws Exception {
        invokeAntMojo("ant-moduleinfo-test");
    }

    @Test
    public void testProjectWithSisu() throws Exception {
        invokeAntMojo("ant-sisu-test");
    }

    @Test
    public void testProjectWithBnd() throws Exception {
        // bndwrap wraps the already-built jar in place, so it must run after <jar>, not before.
        // bnd isn't resolvable in this hermetic test repo, and there's no present/missing gating
        // anymore (see AntExtensionWriter.writeBndWrapSequence), so the build must fail naturally.
        File testPom = new File("src/test/resources/unit/ant-bnd-test");
        File antBasedir = new File("target/test/unit/ant-bnd-test/");

        FileUtils.copyFile(new File(testPom, "bnd.bnd"), new File(antBasedir, "bnd.bnd"));

        AntMojo mojo = (AntMojo) rule.lookupMojo("ant", new File(testPom, "pom.xml"));
        mojo.execute();

        String mavenBuildXml = FileUtils.fileRead(new File(antBasedir, AntBuildWriter.DEFAULT_MAVEN_BUILD_FILENAME));

        int jarTaskIndex = mavenBuildXml.indexOf("<jar jarfile=\"${maven.build.dir}/${maven.build.finalName}.jar\"");
        int bndwrapIndex = mavenBuildXml.indexOf("<bndwrap");
        assertTrue("jar task not found", jarTaskIndex >= 0);
        assertTrue("bndwrap task not found", bndwrapIndex >= 0);
        assertTrue("bndwrap must run after the jar it wraps", jarTaskIndex < bndwrapIndex);

        // ant-bnd-test/bnd.bnd is a real physical file, not inline pom config: must be <copy>-ed
        // live, not snapshotted into the build via <echo>.
        assertTrue(
                "physical bnd.bnd must be copied, not re-serialized", mavenBuildXml.contains("<copy file=\"bnd.bnd\""));

        FileUtils.copyDirectoryStructure(new File(testPom, "src"), new File(antBasedir, "src"));

        try {
            AntWrapper.invoke(new File(antBasedir, AntBuildWriter.DEFAULT_BUILD_FILENAME));
            fail("expected the build to fail fast on missing bnd");
        } catch (BuildException e) {
            assertTrue(
                    "expected a bndwrap-related failure, got: " + e.getMessage(),
                    e.getMessage().contains("bndwrap"));
        }
    }

    @Test
    public void testProjectWithBndInlineInstructions() throws Exception {
        // no bnd.bnd file here: instructions come from the pom's <bnd> config instead, so they
        // must be echoed fresh, not copied from a file that doesn't exist.
        File testPom = new File("src/test/resources/unit/ant-bnd-inline-test");
        File antBasedir = new File("target/test/unit/ant-bnd-inline-test/");

        AntMojo mojo = (AntMojo) rule.lookupMojo("ant", new File(testPom, "pom.xml"));
        mojo.execute();

        String mavenBuildXml = FileUtils.fileRead(new File(antBasedir, AntBuildWriter.DEFAULT_MAVEN_BUILD_FILENAME));

        assertFalse(
                "no bnd.bnd file exists, so it must not be copied", mavenBuildXml.contains("<copy file=\"bnd.bnd\""));
        assertTrue(
                "inline instructions must be echoed into bnd.bnd",
                mavenBuildXml.contains("Bundle-SymbolicName: ant-bnd-inline-test"));

        int jarTaskIndex = mavenBuildXml.indexOf("<jar jarfile=\"${maven.build.dir}/${maven.build.finalName}.jar\"");
        int bndwrapIndex = mavenBuildXml.indexOf("<bndwrap");
        assertTrue("jar task not found", jarTaskIndex >= 0);
        assertTrue("bndwrap task not found", bndwrapIndex >= 0);
        assertTrue("bndwrap must run after the jar it wraps", jarTaskIndex < bndwrapIndex);

        FileUtils.copyDirectoryStructure(new File(testPom, "src"), new File(antBasedir, "src"));

        try {
            AntWrapper.invoke(new File(antBasedir, AntBuildWriter.DEFAULT_BUILD_FILENAME));
            fail("expected the build to fail fast on missing bnd");
        } catch (BuildException e) {
            assertTrue(
                    "expected a bndwrap-related failure, got: " + e.getMessage(),
                    e.getMessage().contains("bndwrap"));
        }
    }

    @Test
    public void testProjectWithBundlePluginInstructions() throws Exception {
        // maven-bundle-plugin's <instructions> are already discrete key/value pairs, so they must
        // go through <propertyfile><entry .../></propertyfile>, not a hand-built <echo> string.
        File testPom = new File("src/test/resources/unit/ant-bundle-instructions-test");
        File antBasedir = new File("target/test/unit/ant-bundle-instructions-test/");

        AntMojo mojo = (AntMojo) rule.lookupMojo("ant", new File(testPom, "pom.xml"));
        mojo.execute();

        String mavenBuildXml = FileUtils.fileRead(new File(antBasedir, AntBuildWriter.DEFAULT_MAVEN_BUILD_FILENAME));

        assertTrue("echo bnd.bnd task not found", mavenBuildXml.contains("<echo file=\"${maven.build.dir}/bnd.bnd\""));
        assertTrue(
                "Bundle-SymbolicName entry not found",
                mavenBuildXml.contains("Bundle-SymbolicName: ant-bundle-instructions-test"));
        assertTrue("Export-Package entry not found", mavenBuildXml.contains("Export-Package: *"));
        assertTrue(
                "-exportcontents directive not found",
                mavenBuildXml.contains("-exportcontents: org.apache.maven.plugin.ant.*"));
        assertTrue("-noee directive not found", mavenBuildXml.contains("-noee"));
        assertFalse("-noee directive should not have colon", mavenBuildXml.contains("-noee:"));

        int jarTaskIndex = mavenBuildXml.indexOf("<jar jarfile=\"${maven.build.dir}/${maven.build.finalName}.jar\"");
        int bndwrapIndex = mavenBuildXml.indexOf("<bndwrap");
        assertTrue("jar task not found", jarTaskIndex >= 0);
        assertTrue("bndwrap task not found", bndwrapIndex >= 0);
        assertTrue("bndwrap must run after the jar it wraps", jarTaskIndex < bndwrapIndex);

        FileUtils.copyDirectoryStructure(new File(testPom, "src"), new File(antBasedir, "src"));

        try {
            AntWrapper.invoke(new File(antBasedir, AntBuildWriter.DEFAULT_BUILD_FILENAME));
            fail("expected the build to fail fast on missing bnd");
        } catch (BuildException e) {
            assertTrue(
                    "expected a bndwrap-related failure, got: " + e.getMessage(),
                    e.getMessage().contains("bndwrap"));
        }
    }

    @Test
    public void testProjectWithJavacc() throws Exception {
        // javacc isn't resolvable in this hermetic test repo, so the javacc target must fail
        // rather than silently skip generation and let compile fail confusingly later. The .jj
        // fixture must be in place before the mojo runs: getGrammarFiles() scans for it at
        // generation time, same as hasBndFile() does for bnd.bnd.
        File testPom = new File("src/test/resources/unit/ant-javacc-test");
        File antBasedir = new File("target/test/unit/ant-javacc-test/");

        FileUtils.copyDirectoryStructure(new File(testPom, "src"), new File(antBasedir, "src"));

        AntMojo mojo = (AntMojo) rule.lookupMojo("ant", new File(testPom, "pom.xml"));
        mojo.execute();

        try {
            AntWrapper.invoke(new File(antBasedir, AntBuildWriter.DEFAULT_BUILD_FILENAME));
            fail("expected the build to fail fast on missing javacc");
        } catch (BuildException e) {
            // no custom message anymore: javacc.jar isn't resolvable in this hermetic repo, so the
            // javacc target's own <copy> (now failonerror-enabled) fails naturally, right in the
            // first target that needs it - still early, just via Ant's own error instead of ours.
            assertTrue(
                    "expected a fail-fast error, got: " + e.getMessage(),
                    e.getMessage().contains("javacc-7.0.12.jar"));
        }
    }

    @Test
    public void testProjectWithJavadoc() throws Exception {
        invokeAntMojo("ant-javadoc-test");
    }

    private void invokeAntMojo(String testProject) throws Exception {
        File testPom = new File("src/test/resources/unit/" + testProject);

        // bnd.bnd must be in place before the mojo runs: AntBuildWriterUtil.hasBndFile() looks for
        // it at generation time to decide between copying the real file vs. echoing pom instructions.
        File antBasedir = new File("target/test/unit/" + testProject + "/");
        File bndFile = new File(testPom, "bnd.bnd");
        if (bndFile.exists()) {
            FileUtils.copyFile(bndFile, new File(antBasedir, "bnd.bnd"));
        }

        AntMojo mojo = (AntMojo) rule.lookupMojo("ant", new File(testPom, "pom.xml"));
        assertNotNull("Mojo could not be looked up", mojo);
        mojo.execute();

        MavenProject currentProject = (MavenProject) rule.getVariableValueFromObject(mojo, "project");

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
}
