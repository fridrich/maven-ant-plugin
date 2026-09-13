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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DependencyUnpackItemTest {

    @Test
    public void testDefaultValues() {
        DependencyUnpackItem item = new DependencyUnpackItem();
        assertEquals("jar", item.getType());
        assertNull(item.getGroupId());
        assertNull(item.getArtifactId());
        assertNull(item.getVersion());
        assertNull(item.getClassifier());
        assertNull(item.getOutputDirectory());
        assertNotNull(item.getIncludes());
        assertTrue(item.getIncludes().isEmpty());
        assertNotNull(item.getExcludes());
        assertTrue(item.getExcludes().isEmpty());
    }

    @Test
    public void testSettersAndGetters() {
        DependencyUnpackItem item = new DependencyUnpackItem();
        item.setGroupId("org.apache.maven");
        item.setArtifactId("apache-maven");
        item.setVersion("3.9.9");
        item.setType("tar.gz");
        item.setClassifier("bin");
        item.setOutputDirectory("target/extracted");
        item.getIncludes().add("**/*.so");
        item.getExcludes().add("**/*.dll");

        assertEquals("org.apache.maven", item.getGroupId());
        assertEquals("apache-maven", item.getArtifactId());
        assertEquals("3.9.9", item.getVersion());
        assertEquals("tar.gz", item.getType());
        assertEquals("bin", item.getClassifier());
        assertEquals("target/extracted", item.getOutputDirectory());
        assertEquals(1, item.getIncludes().size());
        assertEquals("**/*.so", item.getIncludes().get(0));
        assertEquals(1, item.getExcludes().size());
        assertEquals("**/*.dll", item.getExcludes().get(0));
    }
}
