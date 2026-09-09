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

import javax.inject.Inject;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.testing.PlexusTest;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PlexusTest
public class AntBuildWriterUtilTest {

    @Inject
    private ProjectBuilder projectBuilder;

    private MavenProject readMavenProject(File basedir) throws Exception {
        File pom = new File(basedir, "pom.xml");
        ProjectBuildingRequest configuration = new DefaultProjectBuildingRequest();
        configuration.setRepositorySession(new DefaultRepositorySystemSession());
        return projectBuilder.build(pom, configuration).getProject();
    }

    @Test
    public void testGetMavenCompilerPluginConfiguration() throws Exception {
        File testPom = new File("src/test/resources/unit/ant-compiler-config-test");
        MavenProject project = readMavenProject(testPom);

        assertEquals("true", AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "debug", null));

        assertNotNull(AntBuildWriterUtil.getMavenCompilerPluginOptions(project, "includes", null));
        assertEquals(2, AntBuildWriterUtil.getMavenCompilerPluginOptions(project, "includes", null).length);
        assertNotNull(AntBuildWriterUtil.getMavenCompilerPluginOptions(project, "excludes", null));
        assertEquals(1, AntBuildWriterUtil.getMavenCompilerPluginOptions(project, "excludes", null).length);
    }

    @Test
    public void testGetMavenWarPluginConfiguration() throws Exception {
        File testPom = new File("src/test/resources/unit/ant-war-config-test");
        MavenProject project = readMavenProject(testPom);

        assertEquals("mywebapp", AntBuildWriterUtil.getMavenWarPluginBasicOption(project, "warName", null));
        assertTrue(AntBuildWriterUtil.getMavenWarPluginBasicOption(project, "webXml", null)
                .endsWith("/src/main/webapp/WEB-INF/web.xml"));
    }

    @Test
    public void testGetMavenJavadocPluginConfiguration() throws Exception {
        File testPom = new File("src/test/resources/unit/ant-javadoc-test");
        MavenProject project = readMavenProject(testPom);

        assertNotNull(AntBuildWriterUtil.getMavenJavadocPluginOptions(project, "links", null));
        assertEquals(2, AntBuildWriterUtil.getMavenJavadocPluginOptions(project, "links", null).length);

        Map[] options = AntBuildWriterUtil.getMavenJavadocPluginOptions(project, "tags", null);
        assertNotNull(options);
        assertEquals(1, options.length);
        assertEquals(1, options[0].size());
        Map<?, ?> properties = (Map<?, ?>) options[0].get("tag");
        assertNotNull(properties);
        assertEquals("requirement", properties.get("name"));
        assertEquals("a", properties.get("placement"));
        assertEquals("Software Requirement:", properties.get("head"));
    }

    @Test
    public void testGetSingularForm() throws Exception {
        assertEquals("property", AntBuildWriterUtil.getSingularForm("properties"));
        assertEquals("branch", AntBuildWriterUtil.getSingularForm("branches"));
        assertEquals("report", AntBuildWriterUtil.getSingularForm("reports"));
        assertEquals("", AntBuildWriterUtil.getSingularForm("singular"));
        assertEquals("", AntBuildWriterUtil.getSingularForm(null));
    }

    @Test
    public void testToRelative() throws Exception {
        assertEquals("relative", AntBuildWriterUtil.toRelative(new File("/home"), "relative"));
        assertEquals("dir", AntBuildWriterUtil.toRelative(new File("home"), new File("home/dir").getAbsolutePath()));
        assertEquals("dir", AntBuildWriterUtil.toRelative(new File("/home"), new File("/home/dir").getAbsolutePath()));
        assertEquals(
                "dir/",
                AntBuildWriterUtil.toRelative(new File("/home"), new File("/home/dir").getAbsolutePath() + "/"));
        assertEquals(
                "dir/sub",
                AntBuildWriterUtil.toRelative(new File("/home"), new File("/home/dir/sub").getAbsolutePath()));
        assertEquals(".", AntBuildWriterUtil.toRelative(new File("/home"), new File("/home").getAbsolutePath()));
        assertEquals("./", AntBuildWriterUtil.toRelative(new File("/home"), new File("/home").getAbsolutePath() + "/"));
    }

    @Test
    public void testMultiReleaseExecutionDetection() {
        MavenProject project = new MavenProject();
        project.getBuild().setDirectory("target");
        project.getBuild().setOutputDirectory("target/classes");
        project.getBuild().setSourceDirectory("src/main/java");

        CompilerExecution defaultExec11 = new CompilerExecution(
                "default", "11", "11", "11", Collections.singletonList("src/main/java"), null, null);
        assertFalse(AntBuildWriterUtil.isMultiReleaseExecution(defaultExec11, 11, project));

        CompilerExecution defaultCompileExec11 = new CompilerExecution(
                "default-compile", "11", "11", "11", Collections.singletonList("src/main/java"), null, null);
        assertFalse(AntBuildWriterUtil.isMultiReleaseExecution(defaultCompileExec11, 11, project));

        CompilerExecution java9Exec = new CompilerExecution(
                "compile-java9", "9", "9", "9", Collections.singletonList("src/main/java9"), null, null);
        assertTrue(AntBuildWriterUtil.isMultiReleaseExecution(java9Exec, 8, project));

        List<CompilerExecution> executions = Arrays.asList(defaultExec11);
        assertEquals(11, AntBuildWriterUtil.getBaseCompileVersion(project, executions));
    }

    @Test
    public void testNormalizedOSGiVersion() {
        assertEquals("0.0.0", AntBuildWriterUtil.getNormalizedOSGiVersion(null));
        assertEquals("3.3.0", AntBuildWriterUtil.getNormalizedOSGiVersion("3.3"));
        assertEquals("3.28.2", AntBuildWriterUtil.getNormalizedOSGiVersion("3.28.2"));
        assertEquals("3.28.2.SNAPSHOT", AntBuildWriterUtil.getNormalizedOSGiVersion("3.28.2-SNAPSHOT"));
        assertEquals("3.10.0.rc_1", AntBuildWriterUtil.getNormalizedOSGiVersion("3.10.0-rc-1"));
        assertEquals("1.0.0.beta_2", AntBuildWriterUtil.getNormalizedOSGiVersion("1.0-beta-2"));
        assertEquals("1.2.3.4", AntBuildWriterUtil.getNormalizedOSGiVersion("1.2.3.4"));
    }

    @Test
    public void testSpecificationVersion() {
        assertNull(AntBuildWriterUtil.getSpecificationVersion(null));
        assertEquals("1", AntBuildWriterUtil.getSpecificationVersion("1"));
        assertEquals("1.0", AntBuildWriterUtil.getSpecificationVersion("1.0"));
        assertEquals("1.2", AntBuildWriterUtil.getSpecificationVersion("1.2.3"));
        assertEquals("1.2", AntBuildWriterUtil.getSpecificationVersion("1.2.3.4"));
        assertEquals("3.0", AntBuildWriterUtil.getSpecificationVersion("3.0.0-SNAPSHOT"));
        assertEquals("2.4", AntBuildWriterUtil.getSpecificationVersion("2.4-beta-1"));
    }
}
