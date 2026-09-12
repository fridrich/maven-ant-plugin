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
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AntCleanMojoTest {

    private AntCleanMojo newMojo(Path basedir, boolean deleteCustomFiles) throws IllegalAccessException {
        MavenProject project = new MavenProject();
        project.setFile(new File(basedir.toFile(), "pom.xml"));
        project.setArtifactId("clean-test");

        AntCleanMojo mojo = new AntCleanMojo();
        MojoExtension.setVariableValueToObject(mojo, "project", project);
        MojoExtension.setVariableValueToObject(mojo, "deleteCustomFiles", deleteCustomFiles);
        return mojo;
    }

    @Test
    public void testExecuteWithNoGeneratedFilesDoesNotThrow(@TempDir Path basedir) throws Exception {
        AntCleanMojo mojo = newMojo(basedir, false);
        mojo.execute();
    }

    @Test
    public void testGeneratedFilesAreAlwaysDeleted(@TempDir Path basedir) throws Exception {
        File mavenBuildXml = new File(basedir.toFile(), AntBuildWriter.DEFAULT_MAVEN_BUILD_FILENAME);
        File mavenBuildProperties = new File(basedir.toFile(), AntBuildWriter.DEFAULT_MAVEN_PROPERTIES_FILENAME);
        Files.createFile(mavenBuildXml.toPath());
        Files.createFile(mavenBuildProperties.toPath());

        AntCleanMojo mojo = newMojo(basedir, false);
        mojo.execute();

        assertFalse(mavenBuildXml.exists(), "maven-build.xml should always be deleted");
        assertFalse(mavenBuildProperties.exists(), "maven-build.properties should always be deleted");
    }

    @Test
    public void testCustomBuildXmlKeptByDefault(@TempDir Path basedir) throws Exception {
        File buildXml = new File(basedir.toFile(), AntBuildWriter.DEFAULT_BUILD_FILENAME);
        Files.createFile(buildXml.toPath());

        AntCleanMojo mojo = newMojo(basedir, false);
        mojo.execute();

        assertTrue(buildXml.exists(), "custom build.xml must be kept unless deleteCustomFiles=true");
    }

    @Test
    public void testCustomBuildXmlDeletedWhenRequested(@TempDir Path basedir) throws Exception {
        File buildXml = new File(basedir.toFile(), AntBuildWriter.DEFAULT_BUILD_FILENAME);
        Files.createFile(buildXml.toPath());

        AntCleanMojo mojo = newMojo(basedir, true);
        mojo.execute();

        assertFalse(buildXml.exists(), "custom build.xml must be deleted when deleteCustomFiles=true");
    }
}
