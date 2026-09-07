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
        invokeAntMojo("ant-bnd-test");

        // bnd must write its manifest to a side jar, before the real <jar> task, not overwrite it.
        String mavenBuildXml = org.codehaus.plexus.util.FileUtils.fileRead(
                new File("target/test/unit/ant-bnd-test", AntBuildWriter.DEFAULT_MAVEN_BUILD_FILENAME));

        int bndTaskIndex = mavenBuildXml.indexOf("<bnd ");
        org.junit.Assert.assertTrue("bnd task not found", bndTaskIndex >= 0);
        int bndTaskEnd = mavenBuildXml.indexOf("/>", bndTaskIndex);
        String bndTaskElement = mavenBuildXml.substring(bndTaskIndex, bndTaskEnd);
        org.junit.Assert.assertFalse(
                "bnd task must not write directly to the final packaged jar",
                bndTaskElement.contains("${maven.build.finalName}"));

        org.junit.Assert.assertTrue(
                "bnd instructions must force-include the compiled classes, "
                        + "otherwise -exportcontents alone leaves the jar empty",
                mavenBuildXml.contains("-includeresource: @"));

        int bndAntcallIndex = mavenBuildXml.indexOf("<antcall target=\"-bnd\"/>");
        int jarTaskIndex = mavenBuildXml.indexOf("<jar jarfile=\"${maven.build.dir}/${maven.build.finalName}.jar\"");
        org.junit.Assert.assertTrue("-bnd antcall not found", bndAntcallIndex >= 0);
        org.junit.Assert.assertTrue("jar task not found", jarTaskIndex >= 0);
        org.junit.Assert.assertTrue(
                "-bnd must run before the final jar is built, so its manifest can be picked up",
                bndAntcallIndex < jarTaskIndex);
    }

    @Test
    public void testProjectWithJavacc() throws Exception {
        invokeAntMojo("ant-javacc-test");
    }

    @Test
    public void testProjectWithJavadoc() throws Exception {
        invokeAntMojo("ant-javadoc-test");
    }

    private void invokeAntMojo(String testProject) throws Exception {
        File testPom = new File("src/test/resources/unit/" + testProject);
        AntMojo mojo = (AntMojo) rule.lookupMojo("ant", new File(testPom, "pom.xml"));
        org.junit.Assert.assertNotNull("Mojo could not be looked up", mojo);
        mojo.execute();

        MavenProject currentProject = (MavenProject) rule.getVariableValueFromObject(mojo, "project");

        File antBasedir = new File("target/test/unit/" + testProject + "/");
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

        File bndFile = new File(testPom, "bnd.bnd");
        if (bndFile.exists()) {
            org.codehaus.plexus.util.FileUtils.copyFile(bndFile, new File(antBasedir, "bnd.bnd"));
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
