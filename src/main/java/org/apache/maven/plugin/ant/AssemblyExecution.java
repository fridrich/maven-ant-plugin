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

import java.util.ArrayList;
import java.util.List;

/**
 * Data model representing a maven-assembly-plugin assembly descriptor and execution.
 */
// ponytail: minimal descriptor model supporting fileSets, dependencySets, files, and common archive formats. Upgrade
// path: Plexus Assembly descriptor parser.
public class AssemblyExecution {
    private String id;
    private final List<String> formats = new ArrayList<>();
    private boolean includeBaseDirectory = true;
    private String baseDirectory;
    private String finalName;
    private boolean appendAssemblyId = true;
    private String tarLongFileMode = "gnu";
    private final List<AssemblyFileSet> fileSets = new ArrayList<>();
    private final List<AssemblyDependencySet> dependencySets = new ArrayList<>();
    private final List<AssemblyFile> files = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getFormats() {
        return formats;
    }

    public void addFormat(String format) {
        if (format != null && !format.trim().isEmpty() && !formats.contains(format.trim())) {
            formats.add(format.trim());
        }
    }

    public boolean isIncludeBaseDirectory() {
        return includeBaseDirectory;
    }

    public void setIncludeBaseDirectory(boolean includeBaseDirectory) {
        this.includeBaseDirectory = includeBaseDirectory;
    }

    public String getBaseDirectory() {
        return baseDirectory;
    }

    public void setBaseDirectory(String baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    public String getFinalName() {
        return finalName;
    }

    public void setFinalName(String finalName) {
        this.finalName = finalName;
    }

    public boolean isAppendAssemblyId() {
        return appendAssemblyId;
    }

    public void setAppendAssemblyId(boolean appendAssemblyId) {
        this.appendAssemblyId = appendAssemblyId;
    }

    public String getTarLongFileMode() {
        return tarLongFileMode;
    }

    public void setTarLongFileMode(String tarLongFileMode) {
        this.tarLongFileMode = tarLongFileMode;
    }

    public List<AssemblyFileSet> getFileSets() {
        return fileSets;
    }

    public List<AssemblyDependencySet> getDependencySets() {
        return dependencySets;
    }

    public List<AssemblyFile> getFiles() {
        return files;
    }

    public static class AssemblyFileSet {
        private String directory;
        private String outputDirectory;
        private final List<String> includes = new ArrayList<>();
        private final List<String> excludes = new ArrayList<>();
        private String fileMode;
        private String lineEnding;

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public String getOutputDirectory() {
            return outputDirectory;
        }

        public void setOutputDirectory(String outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        public List<String> getIncludes() {
            return includes;
        }

        public List<String> getExcludes() {
            return excludes;
        }

        public String getFileMode() {
            return fileMode;
        }

        public void setFileMode(String fileMode) {
            this.fileMode = fileMode;
        }

        public String getLineEnding() {
            return lineEnding;
        }

        public void setLineEnding(String lineEnding) {
            this.lineEnding = lineEnding;
        }
    }

    public static class AssemblyDependencySet {
        private String outputDirectory;
        private final List<String> includes = new ArrayList<>();
        private final List<String> excludes = new ArrayList<>();
        private boolean useProjectArtifact = true;
        private String scope;

        public String getOutputDirectory() {
            return outputDirectory;
        }

        public void setOutputDirectory(String outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        public List<String> getIncludes() {
            return includes;
        }

        public List<String> getExcludes() {
            return excludes;
        }

        public boolean isUseProjectArtifact() {
            return useProjectArtifact;
        }

        public void setUseProjectArtifact(boolean useProjectArtifact) {
            this.useProjectArtifact = useProjectArtifact;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }
    }

    public static class AssemblyFile {
        private String source;
        private String outputDirectory;
        private String destName;
        private String fileMode;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getOutputDirectory() {
            return outputDirectory;
        }

        public void setOutputDirectory(String outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        public String getDestName() {
            return destName;
        }

        public void setDestName(String destName) {
            this.destName = destName;
        }

        public String getFileMode() {
            return fileMode;
        }

        public void setFileMode(String fileMode) {
            this.fileMode = fileMode;
        }
    }
}
