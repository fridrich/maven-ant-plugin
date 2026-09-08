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
import org.junit.Rule;
import org.junit.Test;

public class AntMojoTest {

    @Rule
    public MojoRule rule = new MojoRule();

    @Test
    public void testDefaultProject() throws Exception {
        invokeAntMojo("ant-test");
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

        org.codehaus.plexus.util.FileUtils.copyFile(new File(testPom, "bnd.bnd"), new File(antBasedir, "bnd.bnd"));

        AntMojo mojo = (AntMojo) rule.lookupMojo("ant", new File(testPom, "pom.xml"));
        mojo.execute();

        String mavenBuildXml = org.codehaus.plexus.util.FileUtils.fileRead(
                new File(antBasedir, AntBuildWriter.DEFAULT_MAVEN_BUILD_FILENAME));

        int jarTaskIndex = mavenBuildXml.indexOf("<jar jarfile=\"${maven.build.dir}/${maven.build.finalName}.jar\"");
        int bndwrapIndex = mavenBuildXml.indexOf("<bndwrap");
        org.junit.Assert.assertTrue("jar task not found", jarTaskIndex >= 0);
        org.junit.Assert.assertTrue("bndwrap task not found", bndwrapIndex >= 0);
        org.junit.Assert.assertTrue("bndwrap must run after the jar it wraps", jarTaskIndex < bndwrapIndex);

        // ant-bnd-test/bnd.bnd is a real physical file, not inline pom config: must be <copy>-ed
        // live, not snapshotted into the build via <echo>.
        org.junit.Assert.assertTrue(
                "physical bnd.bnd must be copied, not re-serialized", mavenBuildXml.contains("<copy file=\"bnd.bnd\""));

        org.codehaus.plexus.util.FileUtils.copyDirectoryStructure(
                new File(testPom, "src"), new File(antBasedir, "src"));

        try {
            AntWrapper.invoke(new File(antBasedir, AntBuildWriter.DEFAULT_BUILD_FILENAME));
            org.junit.Assert.fail("expected the build to fail fast on missing bnd");
        } catch (org.apache.tools.ant.BuildException e) {
            org.junit.Assert.assertTrue(
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

        String mavenBuildXml = org.codehaus.plexus.util.FileUtils.fileRead(
                new File(antBasedir, AntBuildWriter.DEFAULT_MAVEN_BUILD_FILENAME));

        org.junit.Assert.assertFalse(
                "no bnd.bnd file exists, so it must not be copied", mavenBuildXml.contains("<copy file=\"bnd.bnd\""));
        org.junit.Assert.assertTrue(
                "inline instructions must be echoed into bnd.bnd",
                mavenBuildXml.contains("Bundle-SymbolicName: ant-bnd-inline-test"));

        int jarTaskIndex = mavenBuildXml.indexOf("<jar jarfile=\"${maven.build.dir}/${maven.build.finalName}.jar\"");
        int bndwrapIndex = mavenBuildXml.indexOf("<bndwrap");
        org.junit.Assert.assertTrue("jar task not found", jarTaskIndex >= 0);
        org.junit.Assert.assertTrue("bndwrap task not found", bndwrapIndex >= 0);
        org.junit.Assert.assertTrue("bndwrap must run after the jar it wraps", jarTaskIndex < bndwrapIndex);

        org.codehaus.plexus.util.FileUtils.copyDirectoryStructure(
                new File(testPom, "src"), new File(antBasedir, "src"));

        try {
            AntWrapper.invoke(new File(antBasedir, AntBuildWriter.DEFAULT_BUILD_FILENAME));
            org.junit.Assert.fail("expected the build to fail fast on missing bnd");
        } catch (org.apache.tools.ant.BuildException e) {
            org.junit.Assert.assertTrue(
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

        String mavenBuildXml = org.codehaus.plexus.util.FileUtils.fileRead(
                new File(antBasedir, AntBuildWriter.DEFAULT_MAVEN_BUILD_FILENAME));

        org.junit.Assert.assertTrue("propertyfile task not found", mavenBuildXml.contains("<propertyfile"));
        org.junit.Assert.assertTrue(
                "Bundle-SymbolicName entry not found",
                mavenBuildXml.contains("<entry key=\"Bundle-SymbolicName\" value=\"ant-bundle-instructions-test\""));
        org.junit.Assert.assertTrue(
                "Export-Package entry not found", mavenBuildXml.contains("<entry key=\"Export-Package\" value=\"*\""));
        org.junit.Assert.assertTrue(
                "-exportcontents directive not found",
                mavenBuildXml.contains("<entry key=\"-exportcontents\" value=\"org.apache.maven.plugin.ant.*\""));
        org.junit.Assert.assertTrue(
                "-noee directive not found", mavenBuildXml.contains("<entry key=\"-noee\" value=\"\""));

        int jarTaskIndex = mavenBuildXml.indexOf("<jar jarfile=\"${maven.build.dir}/${maven.build.finalName}.jar\"");
        int bndwrapIndex = mavenBuildXml.indexOf("<bndwrap");
        org.junit.Assert.assertTrue("jar task not found", jarTaskIndex >= 0);
        org.junit.Assert.assertTrue("bndwrap task not found", bndwrapIndex >= 0);
        org.junit.Assert.assertTrue("bndwrap must run after the jar it wraps", jarTaskIndex < bndwrapIndex);

        org.codehaus.plexus.util.FileUtils.copyDirectoryStructure(
                new File(testPom, "src"), new File(antBasedir, "src"));

        try {
            AntWrapper.invoke(new File(antBasedir, AntBuildWriter.DEFAULT_BUILD_FILENAME));
            org.junit.Assert.fail("expected the build to fail fast on missing bnd");
        } catch (org.apache.tools.ant.BuildException e) {
            org.junit.Assert.assertTrue(
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

        org.codehaus.plexus.util.FileUtils.copyDirectoryStructure(
                new File(testPom, "src"), new File(antBasedir, "src"));

        AntMojo mojo = (AntMojo) rule.lookupMojo("ant", new File(testPom, "pom.xml"));
        mojo.execute();

        try {
            AntWrapper.invoke(new File(antBasedir, AntBuildWriter.DEFAULT_BUILD_FILENAME));
            org.junit.Assert.fail("expected the build to fail fast on missing javacc");
        } catch (org.apache.tools.ant.BuildException e) {
            // no custom message anymore: javacc.jar isn't resolvable in this hermetic repo, so the
            // javacc target's own <copy> (now failonerror-enabled) fails naturally, right in the
            // first target that needs it - still early, just via Ant's own error instead of ours.
            org.junit.Assert.assertTrue(
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
            org.codehaus.plexus.util.FileUtils.copyFile(bndFile, new File(antBasedir, "bnd.bnd"));
        }

        AntMojo mojo = (AntMojo) rule.lookupMojo("ant", new File(testPom, "pom.xml"));
        org.junit.Assert.assertNotNull("Mojo could not be looked up", mojo);
        mojo.execute();

        MavenProject currentProject = (MavenProject) rule.getVariableValueFromObject(mojo, "project");

        File antBuild = new File(antBasedir, AntBuildWriter.DEFAULT_BUILD_FILENAME);
        org.junit.Assert.assertTrue(antBuild.exists());
        if (!currentProject.getPackaging().toLowerCase().equals("pom")) {
            File antProperties = new File(antBasedir, AntBuildWriter.DEFAULT_MAVEN_PROPERTIES_FILENAME);
            org.junit.Assert.assertTrue(antProperties.exists());
        }

        File srcDir = new File(testPom, "src");
        if (srcDir.exists()) {
            org.codehaus.plexus.util.FileUtils.copyDirectoryStructure(srcDir, new File(antBasedir, "src"));
        }

        AntWrapper.invoke(antBuild);

        if (!currentProject.getPackaging().toLowerCase().equals("pom")) {
            org.junit.Assert.assertTrue(new File(antBasedir, "target").exists());
            org.junit.Assert.assertTrue(new File(antBasedir, "target/classes").exists());
            org.junit.Assert.assertTrue(
                    new File(antBasedir, "target/" + currentProject.getBuild().getFinalName() + ".jar").exists());

            Properties properties = new Properties();
            properties.load(
                    new FileInputStream(new File(antBasedir, AntBuildWriter.DEFAULT_MAVEN_PROPERTIES_FILENAME)));
            String repo = properties.getProperty("maven.repo.local");
            org.junit.Assert.assertTrue(repo.equals(new File("target/local-repo").getAbsolutePath()));
        }
    }
}
