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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a Modello plugin execution configuration.
 */
public class ModelloExecution {
    private final String id;
    private final String version;
    private final String outputDirectory;
    private final String javaSource;
    private String encoding;
    private boolean packageWithVersion;
    private String velocityBasedir;
    private final List<String> models = new ArrayList<>();
    private final List<String> goals = new ArrayList<>();
    private final List<String> templates = new ArrayList<>();
    private final Map<String, String> params = new LinkedHashMap<>();

    public ModelloExecution(String id, String version, String outputDirectory, String javaSource) {
        this.id = id;
        this.version = version;
        this.outputDirectory = outputDirectory;
        this.javaSource = javaSource;
    }

    public ModelloExecution(ModelloExecution other) {
        this(other.id, other.version, other.outputDirectory, other.javaSource);
        this.encoding = other.encoding;
        this.packageWithVersion = other.packageWithVersion;
        this.velocityBasedir = other.velocityBasedir;
        this.models.addAll(other.models);
        this.goals.addAll(other.goals);
        this.templates.addAll(other.templates);
        this.params.putAll(other.params);
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public String getJavaSource() {
        return javaSource;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public boolean isPackageWithVersion() {
        return packageWithVersion;
    }

    public void setPackageWithVersion(boolean packageWithVersion) {
        this.packageWithVersion = packageWithVersion;
    }

    public String getVelocityBasedir() {
        return velocityBasedir;
    }

    public void setVelocityBasedir(String velocityBasedir) {
        this.velocityBasedir = velocityBasedir;
    }

    public List<String> getModels() {
        return models;
    }

    public List<String> getGoals() {
        return goals;
    }

    public List<String> getTemplates() {
        return templates;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public boolean canMergeWith(ModelloExecution other) {
        if (other == null) {
            return false;
        }
        return Objects.equals(version, other.version)
                && Objects.equals(outputDirectory, other.outputDirectory)
                && Objects.equals(javaSource, other.javaSource)
                && Objects.equals(encoding, other.encoding)
                && packageWithVersion == other.packageWithVersion
                && Objects.equals(velocityBasedir, other.velocityBasedir)
                && Objects.equals(models, other.models)
                && Objects.equals(templates, other.templates)
                && Objects.equals(params, other.params);
    }
}
