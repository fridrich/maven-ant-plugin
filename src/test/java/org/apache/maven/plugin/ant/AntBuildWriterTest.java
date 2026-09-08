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

import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests <code>AntBuildWriter</code>.
 *
 * @author Benjamin Bentmann
 * @version $Id: AntBuildWriterTest.java 1517969 2013-08-27 20:14:02Z krosenvold $
 */
public class AntBuildWriterTest {

    @Test
    public void testGetProjectRepoDirectory() {
        String basedir = new File(System.getProperty("java.io.tmpdir")).getPath();

        // non-project rooted repo URLs
        assertEquals(null, AntBuildWriter.getProjectRepoDirectory("http://maven.apache.org/", basedir));
        assertEquals(null, AntBuildWriter.getProjectRepoDirectory("file:///just-some-test-directory", basedir));

        // RFC-compliant URLs
        assertEquals(
                "",
                AntBuildWriter.getProjectRepoDirectory(new File(basedir).toURI().toString(), basedir));
        assertEquals(
                "dir",
                AntBuildWriter.getProjectRepoDirectory(
                        new File(basedir, "dir").toURI().toString(), basedir));
        assertEquals(
                "dir/subdir",
                AntBuildWriter.getProjectRepoDirectory(
                        new File(basedir, "dir/subdir").toURI().toString(), basedir));

        // not so strict URLs
        assertEquals("", AntBuildWriter.getProjectRepoDirectory("file://" + basedir, basedir));
        assertEquals("dir", AntBuildWriter.getProjectRepoDirectory("file://" + basedir + "/dir", basedir));
        assertEquals(
                "dir/subdir", AntBuildWriter.getProjectRepoDirectory("file://" + basedir + "/dir/subdir", basedir));

        // URLs with encoded characters
        assertEquals(
                "some dir",
                AntBuildWriter.getProjectRepoDirectory(
                        new File(basedir, "some dir").toURI().toString(), basedir));
    }

    @Test
    public void testGetTestFrameworkClassName() {
        MavenProject projectJunit3 = new MavenProject();
        Dependency dep3 = new Dependency();
        dep3.setGroupId("junit");
        dep3.setArtifactId("junit");
        dep3.setVersion("3.8.2");
        dep3.setScope("test");
        projectJunit3.getDependencies().add(dep3);
        assertEquals("junit.framework.Test", AntBuildWriterUtil.getTestFrameworkClassName(projectJunit3));

        MavenProject projectJunit4 = new MavenProject();
        Dependency dep4 = new Dependency();
        dep4.setGroupId("junit");
        dep4.setArtifactId("junit");
        dep4.setVersion("4.13.2");
        dep4.setScope("test");
        projectJunit4.getDependencies().add(dep4);
        assertEquals("org.junit.Test", AntBuildWriterUtil.getTestFrameworkClassName(projectJunit4));

        MavenProject projectJunit5 = new MavenProject();
        Dependency dep5 = new Dependency();
        dep5.setGroupId("org.junit.jupiter");
        dep5.setArtifactId("junit-jupiter-api");
        dep5.setVersion("5.10.0");
        dep5.setScope("test");
        projectJunit5.getDependencies().add(dep5);
        assertEquals("org.junit.jupiter.api.Test", AntBuildWriterUtil.getTestFrameworkClassName(projectJunit5));

        MavenProject projectTestng = new MavenProject();
        Dependency depTestng = new Dependency();
        depTestng.setGroupId("org.testng");
        depTestng.setArtifactId("testng");
        depTestng.setVersion("7.8.0");
        depTestng.setScope("test");
        projectTestng.getDependencies().add(depTestng);
        assertEquals("org.testng.annotations.Test", AntBuildWriterUtil.getTestFrameworkClassName(projectTestng));

        MavenProject projectEmpty = new MavenProject();
        assertEquals("org.junit.jupiter.api.Test", AntBuildWriterUtil.getTestFrameworkClassName(projectEmpty));
    }
}
