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
import java.util.List;
import java.util.Map;

import org.apache.maven.project.MavenProject;

/**
 * Represent a Maven compiler plugin execution or profile configuration.
 */
public class CompilerExecution {
    private final String id;
    private final String release;
    private final String source;
    private final String target;
    private final List<String> compileSourceRoots;
    private final Map[] includes;
    private final Map[] excludes;

    public CompilerExecution(
            String id,
            String release,
            String source,
            String target,
            List<String> compileSourceRoots,
            Map[] includes,
            Map[] excludes) {
        this.id = id;
        this.release = release;
        this.source = source;
        this.target = target;
        this.compileSourceRoots = compileSourceRoots;
        this.includes = includes;
        this.excludes = excludes;
    }

    public String getId() {
        return id;
    }

    public String getRelease() {
        return release;
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public List<String> getCompileSourceRoots() {
        return compileSourceRoots;
    }

    public Map[] getIncludes() {
        return includes;
    }

    public Map[] getExcludes() {
        return excludes;
    }

    public boolean isModuleInfo(MavenProject project) {
        if (includes != null) {
            for (int i = 0; i < includes.length; i++) {
                Map map = includes[i];
                if (map != null && map.containsKey("include")) {
                    Object includeObj = map.get("include");
                    if (includeObj instanceof String) {
                        String include = (String) includeObj;
                        if (include.contains("module-info.java")) {
                            return true;
                        }
                    }
                }
            }
        }
        for (int i = 0; i < compileSourceRoots.size(); i++) {
            String root = compileSourceRoots.get(i);
            if (root != null) {
                File rootFile = new File(root);
                if (rootFile.exists() && new File(rootFile, "module-info.java").exists()) {
                    return true;
                }
            }
        }
        return false;
    }
}
