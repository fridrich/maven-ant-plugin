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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompilerExecutionTest {

    @Test
    public void testIsModuleInfoTrueViaExplicitInclude() {
        Map<String, Object> include = new HashMap<>();
        include.put("include", "module-info.java");

        CompilerExecution exec =
                new CompilerExecution("default", null, "9", "9", Collections.emptyList(), new Map[] {include}, null);

        assertTrue(exec.isModuleInfo(null));
    }

    @Test
    public void testIsModuleInfoTrueViaSourceRootScan(@TempDir Path sourceRoot) throws Exception {
        Files.createFile(sourceRoot.resolve("module-info.java"));

        CompilerExecution exec = new CompilerExecution(
                "default", null, "9", "9", Collections.singletonList(sourceRoot.toString()), null, null);

        assertTrue(exec.isModuleInfo(null));
    }

    @Test
    public void testIsModuleInfoFalseWhenAbsent(@TempDir Path sourceRoot) {
        CompilerExecution exec = new CompilerExecution(
                "default", null, "9", "9", Collections.singletonList(sourceRoot.toString()), null, null);

        assertFalse(exec.isModuleInfo(null));
    }

    @Test
    public void testIsModuleInfoFalseForNonExistentSourceRoot() {
        CompilerExecution exec = new CompilerExecution(
                "default", null, "9", "9", Collections.singletonList(new File("does/not/exist").getPath()), null, null);

        assertFalse(exec.isModuleInfo(null));
    }
}
