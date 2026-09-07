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
import java.util.Map;

import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AntBuildWriterUtilTest {

    @Rule
    public MojoRule rule = new MojoRule();

    @Test
    public void testGetMavenCompilerPluginConfiguration() throws Exception {
        File testPom = new File("src/test/resources/unit/ant-compiler-config-test");
        MavenProject project = rule.readMavenProject(testPom);

        assertEquals("true", AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "debug", null));

        assertNotNull(AntBuildWriterUtil.getMavenCompilerPluginOptions(project, "includes", null));
        assertEquals(2, AntBuildWriterUtil.getMavenCompilerPluginOptions(project, "includes", null).length);
        assertNotNull(AntBuildWriterUtil.getMavenCompilerPluginOptions(project, "excludes", null));
        assertEquals(1, AntBuildWriterUtil.getMavenCompilerPluginOptions(project, "excludes", null).length);
    }

    @Test
    public void testGetMavenWarPluginConfiguration() throws Exception {
        File testPom = new File("src/test/resources/unit/ant-war-config-test");
        MavenProject project = rule.readMavenProject(testPom);

        assertEquals("mywebapp", AntBuildWriterUtil.getMavenWarPluginBasicOption(project, "warName", null));
        assertTrue(AntBuildWriterUtil.getMavenWarPluginBasicOption(project, "webXml", null)
                .endsWith("/src/main/webapp/WEB-INF/web.xml"));
    }

    @Test
    public void testGetMavenJavadocPluginConfiguration() throws Exception {
        File testPom = new File("src/test/resources/unit/ant-javadoc-test");
        MavenProject project = rule.readMavenProject(testPom);

        assertNotNull(AntBuildWriterUtil.getMavenJavadocPluginOptions(project, "links", null));
        assertEquals(2, AntBuildWriterUtil.getMavenJavadocPluginOptions(project, "links", null).length);

        Map[] options = AntBuildWriterUtil.getMavenJavadocPluginOptions(project, "tags", null);
        assertNotNull(options);
        assertEquals(1, options.length);
        assertEquals(1, options[0].size());
        Map properties = (Map) options[0].get("tag");
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
}
