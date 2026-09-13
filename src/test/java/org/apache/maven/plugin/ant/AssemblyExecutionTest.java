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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AssemblyExecutionTest {

    @Test
    public void testDefaultValues() {
        AssemblyExecution exec = new AssemblyExecution();
        assertNull(exec.getId());
        assertNull(exec.getBaseDirectory());
        assertNull(exec.getFinalName());
        assertTrue(exec.isIncludeBaseDirectory());
        assertTrue(exec.isAppendAssemblyId());
        assertEquals("gnu", exec.getTarLongFileMode());
        assertNotNull(exec.getFormats());
        assertTrue(exec.getFormats().isEmpty());
        assertNotNull(exec.getFileSets());
        assertTrue(exec.getFileSets().isEmpty());
        assertNotNull(exec.getDependencySets());
        assertTrue(exec.getDependencySets().isEmpty());
        assertNotNull(exec.getFiles());
        assertTrue(exec.getFiles().isEmpty());
    }

    @Test
    public void testSettersAndGetters() {
        AssemblyExecution exec = new AssemblyExecution();
        exec.setId("bin");
        assertEquals("bin", exec.getId());

        exec.setBaseDirectory("custom-base");
        assertEquals("custom-base", exec.getBaseDirectory());

        exec.setFinalName("my-artifact-1.0");
        assertEquals("my-artifact-1.0", exec.getFinalName());

        exec.setIncludeBaseDirectory(false);
        assertFalse(exec.isIncludeBaseDirectory());

        exec.setAppendAssemblyId(false);
        assertFalse(exec.isAppendAssemblyId());

        exec.setTarLongFileMode("posix");
        assertEquals("posix", exec.getTarLongFileMode());
    }

    @Test
    public void testAddFormat() {
        AssemblyExecution exec = new AssemblyExecution();
        exec.addFormat("tar.gz");
        exec.addFormat("  zip  ");
        exec.addFormat(null);
        exec.addFormat("");
        exec.addFormat("   ");
        exec.addFormat("tar.gz"); // duplicate

        assertEquals(2, exec.getFormats().size());
        assertEquals("tar.gz", exec.getFormats().get(0));
        assertEquals("zip", exec.getFormats().get(1));
    }

    @Test
    public void testAssemblyFileSet() {
        AssemblyExecution.AssemblyFileSet fileSet = new AssemblyExecution.AssemblyFileSet();
        assertNull(fileSet.getDirectory());
        assertNull(fileSet.getOutputDirectory());
        assertNull(fileSet.getFileMode());
        assertNull(fileSet.getLineEnding());
        assertNotNull(fileSet.getIncludes());
        assertNotNull(fileSet.getExcludes());

        fileSet.setDirectory("src/main/resources");
        assertEquals("src/main/resources", fileSet.getDirectory());

        fileSet.setOutputDirectory("res");
        assertEquals("res", fileSet.getOutputDirectory());

        fileSet.setFileMode("0755");
        assertEquals("0755", fileSet.getFileMode());

        fileSet.setLineEnding("unix");
        assertEquals("unix", fileSet.getLineEnding());

        fileSet.getIncludes().add("**/*.properties");
        fileSet.getExcludes().add("**/*.bak");
        assertEquals(1, fileSet.getIncludes().size());
        assertEquals(1, fileSet.getExcludes().size());
        assertEquals("**/*.properties", fileSet.getIncludes().get(0));
        assertEquals("**/*.bak", fileSet.getExcludes().get(0));
    }

    @Test
    public void testAssemblyDependencySet() {
        AssemblyExecution.AssemblyDependencySet depSet = new AssemblyExecution.AssemblyDependencySet();
        assertNull(depSet.getOutputDirectory());
        assertNull(depSet.getScope());
        assertTrue(depSet.isUseProjectArtifact());
        assertNotNull(depSet.getIncludes());
        assertNotNull(depSet.getExcludes());

        depSet.setOutputDirectory("lib");
        assertEquals("lib", depSet.getOutputDirectory());

        depSet.setScope("runtime");
        assertEquals("runtime", depSet.getScope());

        depSet.setUseProjectArtifact(false);
        assertFalse(depSet.isUseProjectArtifact());

        depSet.getIncludes().add("*:*");
        depSet.getExcludes().add("org.junit:*");
        assertEquals(1, depSet.getIncludes().size());
        assertEquals(1, depSet.getExcludes().size());
        assertEquals("*:*", depSet.getIncludes().get(0));
        assertEquals("org.junit:*", depSet.getExcludes().get(0));
    }

    @Test
    public void testAssemblyFile() {
        AssemblyExecution.AssemblyFile file = new AssemblyExecution.AssemblyFile();
        assertNull(file.getSource());
        assertNull(file.getOutputDirectory());
        assertNull(file.getDestName());
        assertNull(file.getFileMode());

        file.setSource("README.txt");
        assertEquals("README.txt", file.getSource());

        file.setOutputDirectory("doc");
        assertEquals("doc", file.getOutputDirectory());

        file.setDestName("README.md");
        assertEquals("README.md", file.getDestName());

        file.setFileMode("0644");
        assertEquals("0644", file.getFileMode());
    }
}
