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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.XMLWriter;
import org.codehaus.plexus.util.xml.XmlWriterUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Write Ant extensions (such as JEP 238 Multi-Release compiles, Sisu indices, and OSGi Bundles).
 */
public class AntExtensionWriter {
    private final MavenProject project;

    public AntExtensionWriter(MavenProject project) {
        this.project = project;
    }

    public boolean isSisuProject() {
        if (project.getBuildPlugins() != null) {
            for (Plugin plugin : project.getBuildPlugins()) {
                if ("sisu-maven-plugin".equals(plugin.getArtifactId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getSisuVersion() {
        if (project.getDependencies() != null) {
            for (Dependency dep : project.getDependencies()) {
                if ("org.eclipse.sisu.plexus".equals(dep.getArtifactId())
                        || "org.eclipse.sisu.inject".equals(dep.getArtifactId())) {
                    return dep.getVersion();
                }
            }
        }
        return "1.1.0"; // default fallback
    }

    public boolean isPlexusProject() {
        if (project == null || AntBuildWriterUtil.isPomPackaging(project)) {
            return false;
        }
        Plugin plugin = findActivePlexusMetadataPlugin();
        if (plugin == null) {
            return false;
        }
        if (plugin.getExecutions() != null && !plugin.getExecutions().isEmpty()) {
            for (PluginExecution exec : plugin.getExecutions()) {
                if (!isInactivePhase(exec.getPhase())) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    public Plugin findActivePlexusMetadataPlugin() {
        if (project == null) {
            return null;
        }
        Plugin childPlugin = findPlexusMetadataPlugin(project.getBuildPlugins());
        if (childPlugin != null) {
            return childPlugin;
        }
        for (MavenProject p = project.getParent(); p != null; p = p.getParent()) {
            Plugin parentPlugin = findPlexusMetadataPlugin(p.getBuildPlugins());
            if (parentPlugin != null) {
                return parentPlugin;
            }
        }
        return null;
    }

    private List<Plugin> getPlexusMetadataFallbackPlugins(Plugin activePlugin) {
        List<Plugin> fallbackPlugins = new ArrayList<>();
        if (project == null) {
            return fallbackPlugins;
        }
        if (project.getBuild() != null && project.getBuild().getPluginManagement() != null) {
            Plugin p = findPlexusMetadataPlugin(
                    project.getBuild().getPluginManagement().getPlugins());
            if (p != null && p != activePlugin) {
                fallbackPlugins.add(p);
            }
        }
        for (MavenProject parent = project.getParent(); parent != null; parent = parent.getParent()) {
            if (parent.getBuild() != null && parent.getBuild().getPluginManagement() != null) {
                Plugin p = findPlexusMetadataPlugin(
                        parent.getBuild().getPluginManagement().getPlugins());
                if (p != null && p != activePlugin) {
                    fallbackPlugins.add(p);
                }
            }
        }
        return fallbackPlugins;
    }

    private Plugin findPlexusMetadataPlugin(List<Plugin> plugins) {
        if (plugins == null) {
            return null;
        }
        for (Plugin plugin : plugins) {
            if ("plexus-component-metadata".equals(plugin.getArtifactId())) {
                return plugin;
            }
        }
        return null;
    }

    private String getPlexusOption(Plugin activePlugin, List<Plugin> fallbackPlugins, String name, String alias) {
        String val = getPlexusOption(activePlugin, name, alias);
        if (val != null) {
            return interpolate(val);
        }
        if (fallbackPlugins != null) {
            for (Plugin p : fallbackPlugins) {
                val = getPlexusOption(p, name, alias);
                if (val != null) {
                    return interpolate(val);
                }
            }
        }
        return null;
    }

    private String getPlexusOption(Plugin plugin, String name, String alias) {
        if (plugin == null) {
            return null;
        }
        Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();
        if (config != null) {
            if (config.getChild(name) != null) {
                return config.getChild(name).getValue();
            }
            if (alias != null && config.getChild(alias) != null) {
                return config.getChild(alias).getValue();
            }
        }
        if (plugin.getExecutions() != null) {
            for (PluginExecution exec : plugin.getExecutions()) {
                Xpp3Dom execConfig = (Xpp3Dom) exec.getConfiguration();
                if (execConfig != null) {
                    if (execConfig.getChild(name) != null) {
                        return execConfig.getChild(name).getValue();
                    }
                    if (alias != null && execConfig.getChild(alias) != null) {
                        return execConfig.getChild(alias).getValue();
                    }
                }
            }
        }
        return null;
    }

    public boolean isJflexProject() {
        if (project.getBuildPlugins() != null) {
            for (Plugin plugin : project.getBuildPlugins()) {
                if ("jflex-maven-plugin".equals(plugin.getArtifactId())) {
                    return true;
                }
            }
        }
        return false;
    }

    // Pinned, not the jflex-maven-plugin version - separate artifact, unrelated release cycle.
    public String getJflexVersion() {
        return "1.9.1";
    }

    public static class JflexExecution {
        private final String id;
        private final Xpp3Dom configuration;

        public JflexExecution(String id, Xpp3Dom configuration) {
            this.id = id;
            this.configuration = configuration;
        }

        public String getId() {
            return id;
        }

        public Xpp3Dom getConfiguration() {
            return configuration;
        }
    }

    public List<JflexExecution> getJflexExecutions() {
        List<JflexExecution> executions = new ArrayList<>();
        if (project.getBuildPlugins() != null) {
            for (Plugin plugin : project.getBuildPlugins()) {
                if ("jflex-maven-plugin".equals(plugin.getArtifactId())) {
                    if (plugin.getExecutions() != null) {
                        for (PluginExecution exec : plugin.getExecutions()) {
                            executions.add(new JflexExecution(exec.getId(), (Xpp3Dom) exec.getConfiguration()));
                        }
                    } else if (plugin.getConfiguration() != null) {
                        executions.add(new JflexExecution("default", (Xpp3Dom) plugin.getConfiguration()));
                    }
                }
            }
        }
        return executions;
    }

    public boolean isCupProject() {
        if (project.getBuildPlugins() != null) {
            for (Plugin plugin : project.getBuildPlugins()) {
                if ("cup-maven-plugin".equals(plugin.getArtifactId())
                        || "javacup-maven-plugin".equals(plugin.getArtifactId())) {
                    return true;
                }
            }
        }
        return false;
    }

    // Pinned, not the cup/javacup-maven-plugin version - separate artifact, unrelated versioning.
    public String getCupVersion() {
        return "11b-20160615-2";
    }

    public static class CupExecution {
        private final String id;
        private final Xpp3Dom configuration;

        public CupExecution(String id, Xpp3Dom configuration) {
            this.id = id;
            this.configuration = configuration;
        }

        public String getId() {
            return id;
        }

        public Xpp3Dom getConfiguration() {
            return configuration;
        }
    }

    public List<CupExecution> getCupExecutions() {
        List<CupExecution> executions = new ArrayList<>();
        if (project.getBuildPlugins() != null) {
            for (Plugin plugin : project.getBuildPlugins()) {
                if ("cup-maven-plugin".equals(plugin.getArtifactId())
                        || "javacup-maven-plugin".equals(plugin.getArtifactId())) {
                    if (plugin.getExecutions() != null) {
                        for (PluginExecution exec : plugin.getExecutions()) {
                            executions.add(new CupExecution(exec.getId(), (Xpp3Dom) exec.getConfiguration()));
                        }
                    } else if (plugin.getConfiguration() != null) {
                        executions.add(new CupExecution("default", (Xpp3Dom) plugin.getConfiguration()));
                    }
                }
            }
        }
        return executions;
    }

    public boolean isBndProject() {
        if (project.getBuildPlugins() != null) {
            for (Plugin plugin : project.getBuildPlugins()) {
                if ("bnd-maven-plugin".equals(plugin.getArtifactId())
                        || "maven-bundle-plugin".equals(plugin.getArtifactId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isJavaccProject() {
        if (project.getBuildPlugins() != null) {
            for (Plugin plugin : project.getBuildPlugins()) {
                if ("javacc-maven-plugin".equals(plugin.getArtifactId())
                        || "ph-javacc-maven-plugin".equals(plugin.getArtifactId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isTemplatingProject() {
        if (project.getBuildPlugins() != null) {
            for (Plugin plugin : project.getBuildPlugins()) {
                if ("templating-maven-plugin".equals(plugin.getArtifactId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Extra source dirs registered via build-helper-maven-plugin's add-source goal. */
    public List<String> getAddSourceDirs() {
        List<String> dirs = new ArrayList<>();
        if (project.getBuildPlugins() == null) {
            return dirs;
        }
        for (Plugin plugin : project.getBuildPlugins()) {
            if (!"build-helper-maven-plugin".equals(plugin.getArtifactId()) || plugin.getExecutions() == null) {
                continue;
            }
            for (PluginExecution exec : plugin.getExecutions()) {
                Xpp3Dom config = (Xpp3Dom) exec.getConfiguration();
                if (!exec.getGoals().contains("add-source") || config == null) {
                    continue;
                }
                Xpp3Dom sourcesNode = config.getChild("sources");
                if (sourcesNode == null) {
                    continue;
                }
                for (Xpp3Dom sourceNode : sourcesNode.getChildren("source")) {
                    dirs.add(sourceNode.getValue());
                }
            }
        }
        return dirs;
    }

    public List<JavaccExecution> getJavaccExecutions() {
        List<JavaccExecution> executions = new ArrayList<>();
        if (project.getBuildPlugins() != null) {
            for (Plugin plugin : project.getBuildPlugins()) {
                if ("javacc-maven-plugin".equals(plugin.getArtifactId())
                        || "ph-javacc-maven-plugin".equals(plugin.getArtifactId())) {
                    Xpp3Dom pluginConfig = (Xpp3Dom) plugin.getConfiguration();
                    if (plugin.getExecutions() != null) {
                        for (PluginExecution exec : plugin.getExecutions()) {
                            Xpp3Dom execConfig = (Xpp3Dom) exec.getConfiguration();
                            Xpp3Dom mergedConfig = mergeConfigurations(execConfig, pluginConfig);
                            for (String goal : exec.getGoals()) {
                                if ("javacc".equals(goal) || "jjtree".equals(goal) || "jjtree-javacc".equals(goal)) {
                                    executions.add(new JavaccExecution(exec.getId(), goal, mergedConfig));
                                }
                            }
                        }
                    }
                }
            }
        }
        return executions;
    }

    private Xpp3Dom mergeConfigurations(Xpp3Dom dominant, Xpp3Dom recessive) {
        if (dominant == null) {
            return recessive != null ? new Xpp3Dom(recessive) : null;
        }
        if (recessive == null) {
            return new Xpp3Dom(dominant);
        }
        return Xpp3Dom.mergeXpp3Dom(new Xpp3Dom(dominant), recessive);
    }

    private Plugin findModelloPlugin(List<Plugin> plugins) {
        if (plugins == null) {
            return null;
        }
        for (Plugin plugin : plugins) {
            if ("modello-maven-plugin".equals(plugin.getArtifactId())) {
                return plugin;
            }
        }
        return null;
    }

    private PluginExecution findExecutionById(Plugin plugin, String id) {
        if (plugin == null || plugin.getExecutions() == null || id == null) {
            return null;
        }
        for (PluginExecution exec : plugin.getExecutions()) {
            if (id.equals(exec.getId())) {
                return exec;
            }
        }
        return null;
    }

    private boolean isInactivePhase(String phase) {
        if (phase == null) {
            return false;
        }
        String p = phase.toLowerCase();
        return p.contains("site") || "none".equals(p);
    }

    public boolean isModelloProject() {
        return !getMergedModelloExecutions().isEmpty();
    }

    public List<ModelloExecution> getModelloExecutions() {
        List<ModelloExecution> list = new ArrayList<>();
        if (project == null) {
            return list;
        }

        Plugin childPlugin = findModelloPlugin(project.getBuildPlugins());

        Plugin parentBuildPlugin = null;
        if (childPlugin == null) {
            for (MavenProject p = project.getParent(); p != null; p = p.getParent()) {
                parentBuildPlugin = findModelloPlugin(p.getBuildPlugins());
                if (parentBuildPlugin != null) {
                    break;
                }
            }
        }

        if (childPlugin == null && parentBuildPlugin == null) {
            return list;
        }

        Plugin activePlugin = childPlugin != null ? childPlugin : parentBuildPlugin;
        Xpp3Dom childPluginConfig = (Xpp3Dom) activePlugin.getConfiguration();

        List<Plugin> fallbackPlugins = new ArrayList<>();
        if (project.getBuild() != null && project.getBuild().getPluginManagement() != null) {
            Plugin p =
                    findModelloPlugin(project.getBuild().getPluginManagement().getPlugins());
            if (p != null && p != activePlugin) {
                fallbackPlugins.add(p);
            }
        }
        for (MavenProject p = project.getParent(); p != null; p = p.getParent()) {
            if (p.getBuildPlugins() != null) {
                Plugin bp = findModelloPlugin(p.getBuildPlugins());
                if (bp != null && bp != activePlugin && !fallbackPlugins.contains(bp)) {
                    fallbackPlugins.add(bp);
                }
            }
            if (p.getBuild() != null && p.getBuild().getPluginManagement() != null) {
                Plugin mp = findModelloPlugin(p.getBuild().getPluginManagement().getPlugins());
                if (mp != null && mp != activePlugin && !fallbackPlugins.contains(mp)) {
                    fallbackPlugins.add(mp);
                }
            }
        }

        Xpp3Dom effectivePluginConfig = childPluginConfig;
        for (Plugin fallback : fallbackPlugins) {
            effectivePluginConfig = mergeConfigurations(effectivePluginConfig, (Xpp3Dom) fallback.getConfiguration());
        }

        Set<String> handledExecutionIds = new HashSet<>();

        if (activePlugin.getExecutions() != null) {
            for (PluginExecution exec : activePlugin.getExecutions()) {
                handledExecutionIds.add(exec.getId());
                if (isInactivePhase(exec.getPhase())) {
                    continue;
                }
                Xpp3Dom execConfig = (Xpp3Dom) exec.getConfiguration();
                List<String> fallbackGoals = Collections.emptyList();
                for (Plugin fallback : fallbackPlugins) {
                    PluginExecution fallbackExec = findExecutionById(fallback, exec.getId());
                    if (fallbackExec != null) {
                        execConfig = mergeConfigurations(execConfig, (Xpp3Dom) fallbackExec.getConfiguration());
                        if (fallbackGoals.isEmpty()) {
                            fallbackGoals = extractModelloGoals(fallbackExec, execConfig, effectivePluginConfig);
                        }
                    }
                }
                List<String> goals = extractModelloGoals(exec, execConfig, effectivePluginConfig);
                if (goals.isEmpty()) {
                    goals = fallbackGoals;
                }
                if (!goals.isEmpty()) {
                    list.add(createModelloExecution(exec.getId(), execConfig, effectivePluginConfig, goals));
                }
            }
        }

        for (Plugin fallback : fallbackPlugins) {
            if (fallback.getExecutions() == null) {
                continue;
            }
            for (PluginExecution exec : fallback.getExecutions()) {
                if (handledExecutionIds.contains(exec.getId())) {
                    continue;
                }
                handledExecutionIds.add(exec.getId());
                if (isInactivePhase(exec.getPhase())) {
                    continue;
                }
                Xpp3Dom execConfig = (Xpp3Dom) exec.getConfiguration();
                List<String> goals = extractModelloGoals(exec, execConfig, effectivePluginConfig);
                if (!goals.isEmpty()) {
                    list.add(createModelloExecution(exec.getId(), execConfig, effectivePluginConfig, goals));
                }
            }
        }

        if (list.isEmpty() && effectivePluginConfig != null) {
            List<String> goals = extractModelloGoals(null, null, effectivePluginConfig);
            if (!goals.isEmpty()) {
                list.add(createModelloExecution("default", null, effectivePluginConfig, goals));
            }
        }

        return list;
    }

    public List<ModelloExecution> getMergedModelloExecutions() {
        List<ModelloExecution> raw = getModelloExecutions();
        List<ModelloExecution> merged = new ArrayList<>();
        for (ModelloExecution exec : raw) {
            boolean matched = false;
            for (ModelloExecution existing : merged) {
                if (existing.canMergeWith(exec)) {
                    existing.addGoals(exec.getGoals());
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                merged.add(new ModelloExecution(exec));
            }
        }
        return merged;
    }

    private ModelloExecution createModelloExecution(
            String id, Xpp3Dom execConfig, Xpp3Dom pluginConfig, List<String> goals) {
        String version = getModelloOption(execConfig, pluginConfig, "version", project.getVersion());
        String outputDir = getModelloOption(execConfig, pluginConfig, "outputDirectory", "${maven.build.mdoOutputDir}");
        outputDir = interpolate(outputDir);
        if ("${maven.build.dir}/generated-sources/modello".equals(outputDir)) {
            outputDir = "${maven.build.mdoOutputDir}";
        }

        String javaSource = getModelloOption(execConfig, pluginConfig, "javaSource", "8");
        String velocityBasedir = getModelloOption(execConfig, pluginConfig, "velocityBasedir", null);
        if (velocityBasedir != null) {
            velocityBasedir = resolveModelloRelativePath(velocityBasedir);
        }

        String encoding = getModelloOption(execConfig, pluginConfig, "encoding", null);
        if (encoding == null && velocityBasedir != null) {
            encoding = "utf-8";
        }

        String packageWithVersionStr = getModelloOption(execConfig, pluginConfig, "packageWithVersion", "false");
        boolean packageWithVersion = "true".equalsIgnoreCase(packageWithVersionStr);

        String domAsXpp3Str = getModelloOption(execConfig, pluginConfig, "domAsXpp3", "true");
        boolean domAsXpp3 = !"false".equalsIgnoreCase(domAsXpp3Str);

        String basedir = getModelloOption(execConfig, pluginConfig, "basedir", null);
        List<String> models = extractModelloModels(execConfig, pluginConfig, basedir);
        List<String> templates = extractModelloTemplates(execConfig, pluginConfig);
        Map<String, String> params = extractModelloParams(execConfig, pluginConfig);
        Map<String, String> pluralExceptions = extractModelloPluralExceptions(execConfig, pluginConfig);

        ModelloExecution execution = new ModelloExecution(id, version, outputDir, javaSource);
        execution.setEncoding(encoding);
        execution.setPackageWithVersion(packageWithVersion);
        execution.setDomAsXpp3(domAsXpp3);
        execution.setVelocityBasedir(velocityBasedir);
        execution.addModels(models);
        execution.addGoals(goals);
        execution.addTemplates(templates);
        execution.addParams(params);
        execution.addPluralExceptions(pluralExceptions);
        return execution;
    }

    private String getModelloOption(Xpp3Dom execConfig, Xpp3Dom pluginConfig, String name, String defaultValue) {
        if (execConfig != null && execConfig.getChild(name) != null) {
            return execConfig.getChild(name).getValue();
        }
        if (pluginConfig != null && pluginConfig.getChild(name) != null) {
            return pluginConfig.getChild(name).getValue();
        }
        return defaultValue;
    }

    private String resolveModelloRelativePath(String path) {
        if (path == null) {
            return null;
        }
        String p = path.trim().replace("${project.basedir}/", "").replace("${project.basedir}", ".");
        if (new File(p).isAbsolute()) {
            p = AntBuildWriterUtil.toRelative(project.getBasedir(), p);
        }
        return p.replace('\\', '/');
    }

    private List<String> extractModelloModels(Xpp3Dom execConfig, Xpp3Dom pluginConfig, String basedir) {
        List<String> rawModels = extractConfigList(execConfig, "models", "model");
        if (rawModels.isEmpty()) {
            rawModels = extractConfigList(pluginConfig, "models", "model");
        }
        if (rawModels.isEmpty()) {
            File mdoDir = new File(project.getBasedir(), "src/main/mdo");
            if (mdoDir.isDirectory()) {
                File[] files = mdoDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && f.getName().endsWith(".mdo")) {
                            rawModels.add("src/main/mdo/" + f.getName());
                        }
                    }
                }
            }
        }
        String base = resolveModelloRelativePath(basedir);
        List<String> resolved = new ArrayList<>();
        for (String m : rawModels) {
            String rel = resolveModelloRelativePath(m);
            if (base != null && !base.isEmpty() && !".".equals(base)) {
                resolved.add(base + "/" + rel);
            } else {
                resolved.add(rel);
            }
        }
        return resolved;
    }

    private List<String> extractConfigList(Xpp3Dom config, String plural, String singular) {
        List<String> result = new ArrayList<>();
        if (config == null) {
            return result;
        }
        Xpp3Dom pluralNode = config.getChild(plural);
        if (pluralNode != null) {
            for (Xpp3Dom child : pluralNode.getChildren(singular)) {
                if (child.getValue() != null && !child.getValue().trim().isEmpty()) {
                    result.add(child.getValue().trim());
                }
            }
        }
        if (result.isEmpty()) {
            Xpp3Dom singleNode = config.getChild(singular);
            if (singleNode != null
                    && singleNode.getValue() != null
                    && !singleNode.getValue().trim().isEmpty()) {
                result.add(singleNode.getValue().trim());
            }
        }
        return result;
    }

    private List<String> extractModelloGoals(PluginExecution exec, Xpp3Dom execConfig, Xpp3Dom pluginConfig) {
        List<String> goals = new ArrayList<>();
        if (exec != null && exec.getGoals() != null && !exec.getGoals().isEmpty()) {
            goals.addAll(exec.getGoals());
        }
        if (goals.isEmpty()) {
            goals.addAll(extractConfigList(execConfig, "goals", "goal"));
        }
        if (goals.isEmpty()) {
            goals.addAll(extractConfigList(pluginConfig, "goals", "goal"));
        }
        goals.remove("help");
        return goals;
    }

    private List<String> extractModelloTemplates(Xpp3Dom execConfig, Xpp3Dom pluginConfig) {
        List<String> templates = extractConfigList(execConfig, "templates", "template");
        if (templates.isEmpty()) {
            templates = extractConfigList(pluginConfig, "templates", "template");
        }
        return templates;
    }

    private Map<String, String> extractModelloParams(Xpp3Dom execConfig, Xpp3Dom pluginConfig) {
        Map<String, String> params = new LinkedHashMap<>();
        addParamNodes(pluginConfig, params);
        addParamNodes(execConfig, params);
        return params;
    }

    private void addParamNodes(Xpp3Dom config, Map<String, String> params) {
        if (config == null) {
            return;
        }
        Xpp3Dom paramsNode = config.getChild("params");
        if (paramsNode != null) {
            for (Xpp3Dom child : paramsNode.getChildren("param")) {
                String text = child.getValue();
                if (text != null && text.contains("=")) {
                    int eqIdx = text.indexOf('=');
                    String key = text.substring(0, eqIdx).trim();
                    String val = text.substring(eqIdx + 1).trim();
                    params.put(key, val);
                } else if (child.getAttribute("name") != null) {
                    params.put(child.getAttribute("name"), child.getAttribute("value"));
                }
            }
        }
    }

    private Map<String, String> extractModelloPluralExceptions(Xpp3Dom execConfig, Xpp3Dom pluginConfig) {
        Map<String, String> exceptions = new LinkedHashMap<>();
        addPluralExceptionNodes(pluginConfig, exceptions);
        addPluralExceptionNodes(execConfig, exceptions);
        return exceptions;
    }

    private void addPluralExceptionNodes(Xpp3Dom config, Map<String, String> exceptions) {
        if (config == null) {
            return;
        }
        Xpp3Dom peNode = config.getChild("pluralExceptions");
        if (peNode != null) {
            for (Xpp3Dom child : peNode.getChildren()) {
                String plural = child.getName();
                String singular = child.getValue();
                if (plural != null && singular != null && !singular.trim().isEmpty()) {
                    exceptions.put(plural.trim(), singular.trim());
                }
            }
        }
    }

    public String getModelloMdoDir() {
        for (ModelloExecution exec : getMergedModelloExecutions()) {
            for (String model : exec.getModels()) {
                if (model.contains("/")) {
                    return model.substring(0, model.lastIndexOf('/'));
                }
            }
        }
        return "src/main/mdo";
    }

    private List<String> getGrammarFiles(String sourceDirectory, String extension) {
        List<String> files = new ArrayList<>();
        File dir = new File(sourceDirectory);
        if (dir.exists() && dir.isDirectory()) {
            File[] list = dir.listFiles();
            if (list != null) {
                for (File f : list) {
                    if (f.isFile() && f.getName().endsWith(extension)) {
                        files.add(f.getName());
                    }
                }
            }
        }
        return files;
    }

    /**
     * Resolves a single grammar file path or a "dir/*.ext" wildcard to concrete file paths that
     * exist on disk right now, relative to the project basedir.
     */
    private List<String> resolveGrammarFiles(String pathOrPattern) {
        if (!pathOrPattern.contains("*")) {
            return Collections.singletonList(pathOrPattern);
        }
        int lastSlash = pathOrPattern.lastIndexOf('/');
        String dir = lastSlash != -1 ? pathOrPattern.substring(0, lastSlash) : ".";
        String pattern = lastSlash != -1 ? pathOrPattern.substring(lastSlash + 1) : pathOrPattern;
        String extension = pattern.startsWith("*") ? pattern.substring(1) : pattern;

        List<String> resolved = new ArrayList<>();
        for (String fileName : getGrammarFiles(project.getBasedir().getAbsolutePath() + "/" + dir, extension)) {
            resolved.add(dir + "/" + fileName);
        }
        return resolved;
    }

    /**
     * Plain writeLineBreak() leaves the writer's indent state broken for whatever element comes
     * right after, unless writeCommentText() follows to reset it - see writeLineBreak(3 args),
     * which also emits the indent text itself.
     */
    private void writeTargetSeparator(XMLWriter writer, boolean bareTargetFollows) {
        if (bareTargetFollows) {
            XmlWriterUtil.writeLineBreak(writer, 1, 1);
        } else {
            XmlWriterUtil.writeLineBreak(writer);
        }
    }

    /**
     * Comma-joined names of the active source-generation targets ("templates", "javacc", "jflex",
     * "cup"), for use as a depends= value on compile/javadoc. Empty if none apply.
     */
    public String getGenSourceTargets() {
        List<String> names = new ArrayList<>();
        if (isTemplatingProject()) {
            names.add("templates");
        }
        if (isJavaccProject()) {
            names.add("javacc");
        }
        if (isJflexProject()) {
            names.add("jflex");
        }
        if (isCupProject()) {
            names.add("cup");
        }
        if (isModelloProject()) {
            names.add("mdo");
        }
        if (isDependencyUnpackProject()) {
            names.add("unpack-dependencies");
        }
        return StringUtils.join(names.iterator(), ",");
    }

    public void writeGenSourcesTarget(XMLWriter writer) throws IOException {
        if (isTemplatingProject()) {
            XmlWriterUtil.writeCommentText(writer, "Source generation target", 1);

            writer.startElement("target");
            writer.addAttribute("name", "templates");
            writer.addAttribute("depends", "get-deps");
            writer.addAttribute("description", "Generate the sources");

            writer.startElement("mkdir");
            writer.addAttribute("dir", "${maven.build.dir}/generated-sources/java-templates");
            writer.endElement(); // mkdir

            writer.startElement("copy");
            writer.addAttribute("todir", "${maven.build.dir}/generated-sources/java-templates");

            writer.startElement("fileset");
            writer.addAttribute("dir", "src/main/java-templates");
            writer.endElement(); // fileset

            writer.startElement("filterchain");
            writer.startElement("expandproperties");
            writer.endElement(); // expandproperties
            writer.endElement(); // filterchain

            writer.endElement(); // copy

            writer.endElement(); // target

            writeTargetSeparator(writer, isJavaccProject() || isJflexProject() || isCupProject() || isModelloProject());
        }

        if (isJavaccProject()) {
            writer.startElement("target");
            writer.addAttribute("name", "javacc");
            writer.addAttribute("depends", "get-deps");
            writer.addAttribute("description", "Generate the sources");

            writer.startElement("sequential");

            writer.startElement("taskdef");
            writer.addAttribute("name", "javacc");
            writer.addAttribute("classname", "org.apache.tools.ant.taskdefs.optional.javacc.JavaCC");
            writer.addAttribute("classpathref", "build.classpath");
            writer.endElement(); // taskdef

            writer.startElement("taskdef");
            writer.addAttribute("name", "jjtree");
            writer.addAttribute("classname", "org.apache.tools.ant.taskdefs.optional.javacc.JJTree");
            writer.addAttribute("classpathref", "build.classpath");
            writer.endElement(); // taskdef

            List<JavaccExecution> executions = getJavaccExecutions();
            for (JavaccExecution exec : executions) {
                Xpp3Dom config = exec.getConfiguration();
                String goal = exec.getGoal();

                String sourceDirectory = config.getChild("sourceDirectory") != null
                        ? config.getChild("sourceDirectory").getValue()
                        : "src/main/javacc";
                String outputDirectory = config.getChild("outputDirectory") != null
                        ? config.getChild("outputDirectory").getValue()
                        : "${maven.build.dir}/generated-sources/" + (goal.contains("jjtree") ? "jjtree" : "javacc");

                sourceDirectory = interpolate(sourceDirectory);
                outputDirectory = interpolate(outputDirectory);

                writer.startElement("mkdir");
                writer.addAttribute("dir", outputDirectory);
                writer.endElement(); // mkdir

                if ("jjtree".equals(goal) || "jjtree-javacc".equals(goal)) {
                    writeJjtreeTask(writer, sourceDirectory, outputDirectory, config);
                }

                if ("javacc".equals(goal) || "jjtree-javacc".equals(goal)) {
                    writeJavaccTask(writer, sourceDirectory, outputDirectory, config, goal);
                }
            }

            writer.endElement(); // sequential
            writer.endElement(); // target

            writeTargetSeparator(writer, isJflexProject() || isCupProject() || isModelloProject());
        }

        if (isJflexProject()) {
            writeJflexCompileTarget(writer);
        }

        if (isCupProject()) {
            writeCupCompileTarget(writer);
        }

        if (isModelloProject()) {
            writeModelloTarget(writer);
        }

        if (isDependencyUnpackProject()) {
            writeDependencyUnpackTarget(writer);
        }
    }

    public void writeJflexCompileTarget(XMLWriter writer) throws IOException {
        writer.startElement("target");
        writer.addAttribute("name", "jflex");
        writer.addAttribute("depends", "get-deps");
        writer.addAttribute("description", "Generate the sources");

        writer.startElement("sequential");

        writer.startElement("taskdef");
        writer.addAttribute("name", "jflex");
        writer.addAttribute("classname", "jflex.anttask.JFlexTask");
        writer.addAttribute("classpathref", "build.classpath");
        writer.endElement(); // taskdef

        List<JflexExecution> executions = getJflexExecutions();
        for (JflexExecution exec : executions) {
            Xpp3Dom config = exec.getConfiguration();
            // matches jflex-maven-plugin 1.9.1's real defaults
            String outputDirectory = "${maven.build.dir}/generated-sources/jflex";
            if (config != null && config.getChild("outputDirectory") != null) {
                outputDirectory = config.getChild("outputDirectory").getValue();
            }
            outputDirectory = interpolate(outputDirectory);

            writer.startElement("mkdir");
            writer.addAttribute("dir", outputDirectory);
            writer.endElement(); // mkdir

            List<String> lexFiles = new ArrayList<>();
            if (config != null && config.getChild("lexDefinitions") != null) {
                Xpp3Dom lexDefs = config.getChild("lexDefinitions");
                for (Xpp3Dom lexFile : lexDefs.getChildren("lexFile")) {
                    lexFiles.add(lexFile.getValue());
                }
            } else if (config != null && config.getChild("lexFile") != null) {
                lexFiles.add(config.getChild("lexFile").getValue());
            } else {
                lexFiles.add("src/main/jflex/*.flex");
            }

            for (String lexFile : lexFiles) {
                lexFile = interpolate(lexFile);
                // JFlexTask has no fileset support; expand wildcards at generation time
                for (String resolved : resolveGrammarFiles(lexFile)) {
                    writer.startElement("jflex");
                    AntBuildWriterUtil.addWrapAttribute(writer, "jflex", "file", resolved, 3);
                    AntBuildWriterUtil.addWrapAttribute(writer, "jflex", "destdir", outputDirectory, 3);
                    writer.endElement(); // jflex
                }
            }
        }

        writer.endElement(); // sequential
        writer.endElement(); // target

        writeTargetSeparator(writer, isCupProject() || isModelloProject());
    }

    public void writeCupCompileTarget(XMLWriter writer) throws IOException {
        writer.startElement("target");
        writer.addAttribute("name", "cup");
        writer.addAttribute("depends", "get-deps");
        writer.addAttribute("description", "Generate the sources");

        writer.startElement("sequential");

        writer.startElement("taskdef");
        writer.addAttribute("name", "cup");
        writer.addAttribute("classname", "java_cup.anttask.CUPTask");
        writer.addAttribute("classpathref", "build.classpath");
        writer.endElement(); // taskdef

        List<CupExecution> executions = getCupExecutions();
        for (CupExecution exec : executions) {
            Xpp3Dom config = exec.getConfiguration();
            // unverified default: no single canonical cup/javacup-maven-plugin implementation
            String outputDirectory = "${maven.build.dir}/generated-sources/cup";
            if (config != null && config.getChild("outputDirectory") != null) {
                outputDirectory = config.getChild("outputDirectory").getValue();
            }
            outputDirectory = interpolate(outputDirectory);

            writer.startElement("mkdir");
            writer.addAttribute("dir", outputDirectory);
            writer.endElement(); // mkdir

            List<String> cupFiles = new ArrayList<>();
            if (config != null && config.getChild("cupDefinition") != null) {
                cupFiles.add(config.getChild("cupDefinition").getValue());
            } else if (config != null && config.getChild("cupFile") != null) {
                cupFiles.add(config.getChild("cupFile").getValue());
            } else {
                cupFiles.add("src/main/cup/*.cup"); // unverified, see outputDirectory above
                cupFiles.add("src/grammar/*.cup");
            }

            for (String cupFile : cupFiles) {
                cupFile = interpolate(cupFile);
                // CUPTask has no fileset support; expand wildcards at generation time
                for (String resolved : resolveGrammarFiles(cupFile)) {
                    writer.startElement("cup");
                    AntBuildWriterUtil.addWrapAttribute(writer, "cup", "srcfile", resolved, 3);
                    AntBuildWriterUtil.addWrapAttribute(writer, "cup", "destdir", outputDirectory, 3);
                    AntBuildWriterUtil.addWrapAttribute(writer, "cup", "interface", "true", 3);
                    writer.endElement(); // cup
                }
            }
        }

        writer.endElement(); // sequential
        writer.endElement(); // target

        writeTargetSeparator(writer, isModelloProject());
    }

    public void writeModelloTarget(XMLWriter writer) throws IOException {
        XmlWriterUtil.writeCommentText(writer, "Code generation target", 1);

        writer.startElement("target");
        writer.addAttribute("name", "mdo");
        writer.addAttribute("depends", "get-deps");
        writer.addAttribute("description", "Generate sources from mdo files");

        writer.startElement("mkdir");
        writer.addAttribute("dir", "${maven.build.mdoOutputDir}");
        writer.endElement(); // mkdir

        writer.startElement("typedef");
        writer.addAttribute("resource", "com/github/fridrich/modello/ant/antlib.xml");
        writer.endElement(); // typedef

        String mdoDir = getModelloMdoDir();
        for (ModelloExecution exec : getMergedModelloExecutions()) {
            writer.startElement("modello");
            AntBuildWriterUtil.addWrapAttribute(writer, "modello", "version", exec.getVersion(), 3);
            AntBuildWriterUtil.addWrapAttribute(writer, "modello", "outputDirectory", exec.getOutputDirectory(), 3);
            if (exec.getVelocityBasedir() != null) {
                AntBuildWriterUtil.addWrapAttribute(writer, "modello", "velocityBasedir", exec.getVelocityBasedir(), 3);
            }
            if (exec.getJavaSource() != null) {
                AntBuildWriterUtil.addWrapAttribute(writer, "modello", "javaSource", exec.getJavaSource(), 3);
            }
            if (exec.getEncoding() != null) {
                AntBuildWriterUtil.addWrapAttribute(writer, "modello", "encoding", exec.getEncoding(), 3);
            }
            if (exec.isPackageWithVersion()) {
                AntBuildWriterUtil.addWrapAttribute(writer, "modello", "packageWithVersion", "true", 3);
            }
            if (!exec.isDomAsXpp3()) {
                AntBuildWriterUtil.addWrapAttribute(writer, "modello", "domAsXpp3", "false", 3);
            }

            for (String model : exec.getModels()) {
                writer.startElement("model");
                if (model.startsWith(mdoDir + "/")) {
                    writer.addAttribute("file", "${maven.build.mdoDir}/" + model.substring(mdoDir.length() + 1));
                } else {
                    writer.addAttribute("file", model);
                }
                writer.endElement(); // model
            }

            for (String goal : exec.getGoals()) {
                writer.startElement("goal");
                writer.addAttribute("name", goal);
                writer.endElement(); // goal
            }

            for (String template : exec.getTemplates()) {
                writer.startElement("template");
                writer.addAttribute("name", template);
                writer.endElement(); // template
            }

            for (Map.Entry<String, String> entry : exec.getParams().entrySet()) {
                writer.startElement("param");
                writer.addAttribute("name", entry.getKey());
                writer.addAttribute("value", entry.getValue());
                writer.endElement(); // param
            }

            for (Map.Entry<String, String> entry : exec.getPluralExceptions().entrySet()) {
                writer.startElement("pluralException");
                writer.addAttribute("name", entry.getKey());
                writer.addAttribute("value", entry.getValue());
                writer.endElement(); // pluralException
            }

            writer.endElement(); // modello
        }

        writer.endElement(); // target

        writeTargetSeparator(writer, isDependencyUnpackProject());
    }

    public void writeDependencyUnpackTarget(XMLWriter writer) {
        List<DependencyUnpackItem> items = getDependencyUnpackItems();
        if (items.isEmpty()) {
            return;
        }

        XmlWriterUtil.writeCommentText(writer, "Unpack dependencies target", 1);
        writer.startElement("target");
        writer.addAttribute("name", "unpack-dependencies");
        writer.addAttribute("depends", "get-deps");
        writer.addAttribute("description", "Unpack dependencies");

        for (DependencyUnpackItem item : items) {
            String outDir = item.getOutputDirectory() != null
                    ? item.getOutputDirectory()
                    : "${maven.build.dir}/generated-sources/dependency";
            writer.startElement("mkdir");
            writer.addAttribute("dir", outDir);
            writer.endElement(); // mkdir

            writer.startElement("unjar");
            String relPath = getArtifactPath(
                    item.getGroupId(), item.getArtifactId(), item.getVersion(), item.getType(), item.getClassifier());
            writer.addAttribute("src", "${maven.repo.local}/" + relPath);
            writer.addAttribute("dest", outDir);

            if (!item.getIncludes().isEmpty() || !item.getExcludes().isEmpty()) {
                writer.startElement("patternset");
                for (String inc : item.getIncludes()) {
                    writer.startElement("include");
                    writer.addAttribute("name", inc);
                    writer.endElement(); // include
                }
                for (String exc : item.getExcludes()) {
                    writer.startElement("exclude");
                    writer.addAttribute("name", exc);
                    writer.endElement(); // exclude
                }
                writer.endElement(); // patternset
            }

            writer.endElement(); // unjar
        }

        writer.endElement(); // target

        writeTargetSeparator(writer, false);
    }

    public boolean isDependencyUnpackProject() {
        return !getDependencyUnpackItems().isEmpty();
    }

    public List<DependencyUnpackItem> getDependencyUnpackItems() {
        List<DependencyUnpackItem> items = new ArrayList<>();
        if (project == null || project.getBuildPlugins() == null) {
            return items;
        }
        for (Plugin plugin : project.getBuildPlugins()) {
            if ("maven-dependency-plugin".equals(plugin.getArtifactId())) {
                Xpp3Dom pluginConfig = (Xpp3Dom) plugin.getConfiguration();
                if (plugin.getExecutions() != null) {
                    for (PluginExecution exec : plugin.getExecutions()) {
                        if (exec.getGoals() != null && exec.getGoals().contains("unpack")) {
                            Xpp3Dom execConfig = (Xpp3Dom) exec.getConfiguration();
                            Xpp3Dom config = mergeConfigurations(execConfig, pluginConfig);
                            items.addAll(extractArtifactItems(config));
                        }
                    }
                }
            }
        }
        return items;
    }

    private List<DependencyUnpackItem> extractArtifactItems(Xpp3Dom config) {
        List<DependencyUnpackItem> result = new ArrayList<>();
        if (config == null) {
            return result;
        }
        Xpp3Dom itemsNode = config.getChild("artifactItems");
        if (itemsNode != null) {
            for (Xpp3Dom itemNode : itemsNode.getChildren("artifactItem")) {
                DependencyUnpackItem item = new DependencyUnpackItem();
                Xpp3Dom gid = itemNode.getChild("groupId");
                if (gid != null) {
                    item.setGroupId(gid.getValue());
                }
                Xpp3Dom aid = itemNode.getChild("artifactId");
                if (aid != null) {
                    item.setArtifactId(aid.getValue());
                }
                Xpp3Dom ver = itemNode.getChild("version");
                if (ver != null
                        && ver.getValue() != null
                        && !ver.getValue().trim().isEmpty()) {
                    item.setVersion(ver.getValue().trim());
                } else {
                    item.setVersion(resolveDependencyVersion(item.getGroupId(), item.getArtifactId()));
                }
                Xpp3Dom type = itemNode.getChild("type");
                if (type != null && type.getValue() != null) {
                    item.setType(type.getValue().trim());
                }
                Xpp3Dom classifier = itemNode.getChild("classifier");
                if (classifier != null && classifier.getValue() != null) {
                    item.setClassifier(classifier.getValue().trim());
                }
                Xpp3Dom outDir = itemNode.getChild("outputDirectory");
                if (outDir != null && outDir.getValue() != null) {
                    item.setOutputDirectory(toAntOutputDir(outDir.getValue()));
                }
                Xpp3Dom inc = itemNode.getChild("includes");
                if (inc != null && inc.getValue() != null) {
                    for (String s : inc.getValue().split("[,\\n\\r]+")) {
                        if (!s.trim().isEmpty()) {
                            item.getIncludes().add(s.trim());
                        }
                    }
                }
                Xpp3Dom exc = itemNode.getChild("excludes");
                if (exc != null && exc.getValue() != null) {
                    for (String s : exc.getValue().split("[,\\n\\r]+")) {
                        if (!s.trim().isEmpty()) {
                            item.getExcludes().add(s.trim());
                        }
                    }
                }
                if (item.getGroupId() != null && item.getArtifactId() != null && item.getVersion() != null) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    private String resolveDependencyVersion(String groupId, String artifactId) {
        if (groupId == null || artifactId == null) {
            return null;
        }
        if (project.getDependencies() != null) {
            for (Dependency dep : project.getDependencies()) {
                if (groupId.equals(dep.getGroupId()) && artifactId.equals(dep.getArtifactId())) {
                    if (dep.getVersion() != null) {
                        return dep.getVersion();
                    }
                }
            }
        }
        if (project.getDependencyManagement() != null
                && project.getDependencyManagement().getDependencies() != null) {
            for (Dependency dep : project.getDependencyManagement().getDependencies()) {
                if (groupId.equals(dep.getGroupId()) && artifactId.equals(dep.getArtifactId())) {
                    if (dep.getVersion() != null) {
                        return dep.getVersion();
                    }
                }
            }
        }
        if (project.getArtifacts() != null) {
            for (Artifact art : project.getArtifacts()) {
                if (groupId.equals(art.getGroupId()) && artifactId.equals(art.getArtifactId())) {
                    return art.getVersion();
                }
            }
        }
        return null;
    }

    private String toAntOutputDir(String dir) {
        if (dir == null) {
            return null;
        }
        String replaced = dir.trim();
        String buildDirProp = "${project.build.directory}";
        if (replaced.startsWith(buildDirProp)) {
            replaced = "${maven.build.dir}" + replaced.substring(buildDirProp.length());
        }
        String basedirProp = "${project.basedir}/";
        if (replaced.startsWith(basedirProp)) {
            replaced = replaced.substring(basedirProp.length());
        }
        return replaced;
    }

    public String getArtifactPath(String groupId, String artifactId, String version, String type, String classifier) {
        StringBuilder sb = new StringBuilder();
        sb.append(groupId.replace('.', '/'))
                .append('/')
                .append(artifactId)
                .append('/')
                .append(version)
                .append('/')
                .append(artifactId)
                .append('-')
                .append(version);
        if (classifier != null && !classifier.trim().isEmpty()) {
            sb.append('-').append(classifier.trim());
        }
        sb.append('.').append(type != null ? type : "jar");
        return sb.toString();
    }

    private void writeJjtreeTask(XMLWriter writer, String sourceDirectory, String outputDirectory, Xpp3Dom config)
            throws IOException {
        List<String> includes = new ArrayList<>();
        String includeOption = getIncludeFile(config, null);
        if (includeOption != null) {
            includes.add(includeOption);
        } else {
            includes.addAll(getGrammarFiles(project.getBasedir().getAbsolutePath() + "/" + sourceDirectory, ".jjt"));
        }

        if (!includes.isEmpty()) {
            writer.startElement("copy");
            AntBuildWriterUtil.addWrapAttribute(
                    writer,
                    "copy",
                    "file",
                    "${maven.repo.local}/net/java/dev/javacc/javacc/7.0.12/javacc-7.0.12.jar",
                    3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer, "copy", "tofile", "${maven.repo.local}/net/java/dev/javacc/javacc/7.0.12/javacc.jar", 3);
            AntBuildWriterUtil.addWrapAttribute(writer, "copy", "preservelastmodified", "true", 3);
            writer.endElement(); // copy
        }

        for (String include : includes) {
            writer.startElement("jjtree");
            AntBuildWriterUtil.addWrapAttribute(writer, "jjtree", "target", sourceDirectory + "/" + include, 3);
            AntBuildWriterUtil.addWrapAttribute(writer, "jjtree", "outputdirectory", outputDirectory, 3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer, "jjtree", "javacchome", "${maven.repo.local}/net/java/dev/javacc/javacc/7.0.12", 3);
            AntBuildWriterUtil.addWrapAttribute(writer, "jjtree", "static", getOption(config, "isStatic", "false"), 3);
            AntBuildWriterUtil.addWrapAttribute(writer, "jjtree", "multi", getOption(config, "multi", "true"), 3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer, "jjtree", "nodepackage", getOption(config, "nodePackage", null), 3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer, "jjtree", "nodeusesparser", getOption(config, "nodeUsesParser", "true"), 3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer, "jjtree", "buildnodefiles", getOption(config, "buildNodeFiles", "false"), 3);
            writer.endElement(); // jjtree
        }
    }

    private void writeJavaccTask(
            XMLWriter writer, String sourceDirectory, String outputDirectory, Xpp3Dom config, String goal)
            throws IOException {
        List<String> includes = new ArrayList<>();
        String includeOption = getIncludeFile(config, null);
        if (includeOption != null) {
            includes.add(includeOption);
        } else {
            String inputDir = "jjtree-javacc".equals(goal)
                    ? "${maven.build.dir}/generated-sources/jjtree"
                    : (project.getBasedir().getAbsolutePath() + "/" + sourceDirectory);
            String resolvedInputDir = "jjtree-javacc".equals(goal)
                    ? (project.getBasedir().getAbsolutePath() + "/target/generated-sources/jjtree")
                    : inputDir;
            includes.addAll(getGrammarFiles(resolvedInputDir, ".jj"));
        }

        if (!includes.isEmpty()) {
            writer.startElement("copy");
            AntBuildWriterUtil.addWrapAttribute(
                    writer,
                    "copy",
                    "file",
                    "${maven.repo.local}/net/java/dev/javacc/javacc/7.0.12/javacc-7.0.12.jar",
                    3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer, "copy", "tofile", "${maven.repo.local}/net/java/dev/javacc/javacc/7.0.12/javacc.jar", 3);
            AntBuildWriterUtil.addWrapAttribute(writer, "copy", "preservelastmodified", "true", 3);
            writer.endElement(); // copy
        }

        for (String include : includes) {
            writer.startElement("javacc");
            String inputDir =
                    "jjtree-javacc".equals(goal) ? "${maven.build.dir}/generated-sources/jjtree" : sourceDirectory;
            AntBuildWriterUtil.addWrapAttribute(writer, "javacc", "target", inputDir + "/" + include, 3);
            AntBuildWriterUtil.addWrapAttribute(writer, "javacc", "outputdirectory", outputDirectory, 3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer, "javacc", "javacchome", "${maven.repo.local}/net/java/dev/javacc/javacc/7.0.12", 3);
            AntBuildWriterUtil.addWrapAttribute(writer, "javacc", "static", getOption(config, "isStatic", "false"), 3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer, "javacc", "buildparser", getOption(config, "buildParser", "true"), 3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer, "javacc", "debugparser", getOption(config, "debugParser", "false"), 3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer, "javacc", "debuglookahead", getOption(config, "debugLookAhead", "false"), 3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer, "javacc", "debugtokenmanager", getOption(config, "debugTokenManager", "false"), 3);
            writer.endElement(); // javacc
        }
    }

    private String interpolate(String value) {
        if (value == null) {
            return null;
        }
        if (value.contains("${project.build.directory}")) {
            value = value.replace("${project.build.directory}", "${maven.build.dir}");
        }
        if (value.contains("${project.build.outputDirectory}")) {
            value = value.replace("${project.build.outputDirectory}", "${maven.build.outputDir}");
        }
        if (value.contains("${project.build.sourceEncoding}")) {
            value = value.replace("${project.build.sourceEncoding}", "UTF-8");
        }
        if (value.contains("${basedir}")) {
            value = value.replace("${basedir}/", "").replace("${basedir}", ".");
        }
        if (value.contains("${project.basedir}")) {
            value = value.replace("${project.basedir}/", "").replace("${project.basedir}", ".");
        }
        return value;
    }

    private String getOption(Xpp3Dom config, String name, String defaultValue) {
        if (config != null && config.getChild(name) != null) {
            return config.getChild(name).getValue();
        }
        return defaultValue;
    }

    private String getIncludeFile(Xpp3Dom config, String defaultFile) {
        if (config != null) {
            Xpp3Dom inclNode = config.getChild("includes");
            if (inclNode != null) {
                Xpp3Dom child = inclNode.getChild("include");
                if (child != null) {
                    return child.getValue();
                }
            }
        }
        return defaultFile;
    }

    public void writeSisuTarget(XMLWriter writer) {
        XmlWriterUtil.writeCommentText(writer, "Sisu javax.inject.Named generation target", 1);

        writer.startElement("target");
        writer.addAttribute("name", "sisu");
        writer.addAttribute("depends", "compile");
        writer.addAttribute("description", "Generate javax.inject.Name index");

        writer.startElement("sequential");

        writer.startElement("java");
        writer.addAttribute("classname", "org.eclipse.sisu.space.SisuIndex");
        writer.addAttribute("failonerror", "true");
        writer.addAttribute("fork", "true");

        writer.startElement("classpath");
        writer.startElement("path");
        writer.addAttribute("refid", "build.classpath");
        writer.endElement(); // path
        writer.endElement(); // classpath

        writer.startElement("arg");
        writer.addAttribute("value", "${maven.build.outputDir}");
        writer.endElement(); // arg

        writer.endElement(); // java

        writer.startElement("move");
        writer.addAttribute("todir", "${maven.build.outputDir}/META-INF");
        writer.addAttribute("failonerror", "false");
        writer.startElement("fileset");
        writer.addAttribute("dir", "META-INF");
        writer.addAttribute("erroronmissingdir", "false");
        writer.endElement(); // fileset
        writer.endElement(); // move

        writer.endElement(); // sequential
        writer.endElement(); // target

        // writePackageTarget() follows next with its own comment.
        XmlWriterUtil.writeLineBreak(writer);
    }

    public void writeSisuTestTarget(XMLWriter writer) {
        XmlWriterUtil.writeCommentText(writer, "Sisu javax.inject.Named generation target for test classes", 1);

        writer.startElement("target");
        writer.addAttribute("name", "sisu-test");
        writer.addAttribute("depends", "compile-tests");
        writer.addAttribute("description", "Generate javax.inject.Name test index");

        writer.startElement("sequential");

        writer.startElement("java");
        writer.addAttribute("classname", "org.eclipse.sisu.space.SisuIndex");
        writer.addAttribute("failonerror", "true");
        writer.addAttribute("fork", "true");

        writer.startElement("classpath");
        writer.startElement("path");
        writer.addAttribute("refid", "build.classpath");
        writer.endElement(); // path
        writer.startElement("path");
        writer.addAttribute("refid", "build.test.classpath");
        writer.endElement(); // path
        writer.startElement("pathelement");
        writer.addAttribute("location", "${maven.build.outputDir}");
        writer.endElement(); // pathelement
        writer.startElement("pathelement");
        writer.addAttribute("location", "${maven.build.testOutputDir}");
        writer.endElement(); // pathelement
        writer.endElement(); // classpath

        writer.startElement("arg");
        writer.addAttribute("value", "${maven.build.testOutputDir}");
        writer.endElement(); // arg

        writer.endElement(); // java

        writer.startElement("move");
        writer.addAttribute("todir", "${maven.build.testOutputDir}/META-INF");
        writer.addAttribute("failonerror", "false");
        writer.startElement("fileset");
        writer.addAttribute("dir", "META-INF");
        writer.addAttribute("erroronmissingdir", "false");
        writer.endElement(); // fileset
        writer.endElement(); // move

        writer.endElement(); // sequential
        writer.endElement(); // target

        XmlWriterUtil.writeLineBreak(writer);
    }

    public void writePlexusTarget(XMLWriter writer, List<String> compileSourceRoots) {
        writePlexusMetadataTarget(writer, compileSourceRoots, false);
    }

    public void writePlexusTestTarget(XMLWriter writer, List<String> testCompileSourceRoots) {
        writePlexusMetadataTarget(writer, testCompileSourceRoots, true);
    }

    private void writePlexusMetadataTarget(XMLWriter writer, List<String> sourceRoots, boolean isTest) {
        String name = isTest ? "plexus-test" : "plexus";
        String depends = isTest ? "compile-tests" : "compile";
        String desc = isTest ? "Generate Plexus test component.xml" : "Generate Plexus component.xml";
        String classesDir = isTest ? "${maven.build.testOutputDir}" : "${maven.build.outputDir}";
        String prefix = isTest ? "testDir" : "srcDir";

        XmlWriterUtil.writeCommentText(
                writer, "Target to generate Plexus " + (isTest ? "test " : "") + "component.xml", 1);
        writer.startElement("target");
        writer.addAttribute("name", name);
        AntBuildWriterUtil.addWrapAttribute(writer, "target", "depends", depends, 2);
        AntBuildWriterUtil.addWrapAttribute(writer, "target", "description", desc, 2);

        writer.startElement("typedef");
        writer.addAttribute("resource", "org/codehaus/plexus/metadata/ant/antlib.xml");
        writer.endElement();

        writer.startElement("plexus-metadata");
        writer.addAttribute("classesDirectory", classesDir);

        if (!isTest) {
            Plugin activePlugin = findActivePlexusMetadataPlugin();
            List<Plugin> fallbackPlugins = getPlexusMetadataFallbackPlugins(activePlugin);
            String outputFile = getPlexusOption(activePlugin, fallbackPlugins, "outputFile", "generatedMetadata");
            if (outputFile != null) {
                writer.addAttribute("outputFile", outputFile);
            }
            String descriptorsDir =
                    getPlexusOption(activePlugin, fallbackPlugins, "descriptorsDirectory", "staticMetadataDirectory");
            if (descriptorsDir != null) {
                writer.addAttribute("descriptorsDirectory", descriptorsDir);
            }
            String extractors = getPlexusOption(activePlugin, fallbackPlugins, "extractors", null);
            if (extractors != null) {
                writer.addAttribute("extractors", extractors);
            }
        }

        List<String> srcRoots = sourceRoots;
        if ((srcRoots == null || srcRoots.isEmpty()) && project != null) {
            srcRoots = isTest ? project.getTestCompileSourceRoots() : project.getCompileSourceRoots();
        }
        if (srcRoots != null && !srcRoots.isEmpty()) {
            for (int i = 0; i < srcRoots.size(); i++) {
                writer.startElement("sourceDirectory");
                writer.addAttribute("location", "${maven.build." + prefix + "." + i + "}");
                writer.endElement();
            }
        }

        writer.startElement("classpath");
        if (isTest) {
            writer.startElement("path");
            writer.addAttribute("refid", "build.test.classpath");
            writer.endElement();
            writer.startElement("pathelement");
            writer.addAttribute("location", "${maven.build.outputDir}");
            writer.endElement();
        } else {
            writer.addAttribute("refid", "build.classpath");
        }
        writer.endElement();

        writer.endElement();
        writer.endElement();
        XmlWriterUtil.writeLineBreak(writer);
    }

    public void writeBndWrapSequence(XMLWriter writer) {
        writer.startElement("taskdef");
        writer.addAttribute("resource", "aQute/bnd/ant/taskdef.properties");
        writer.addAttribute("classpathref", "build.classpath");
        writer.endElement(); // taskdef

        writeBndDefinitionsFile(writer);

        writer.startElement("bndwrap");
        writer.addAttribute("definitions", "${maven.build.dir}/bnd.bnd");
        AntBuildWriterUtil.addWrapAttribute(
                writer, "bndwrap", "output", "${maven.build.dir}/${maven.build.finalName}.bundle.jar", 3);
        writer.startElement("fileset");
        writer.addAttribute("file", "${maven.build.dir}/${maven.build.finalName}.jar");
        writer.endElement(); // fileset
        writer.endElement(); // bndwrap

        writer.startElement("move");
        writer.addAttribute("file", "${maven.build.dir}/${maven.build.finalName}.bundle.jar");
        AntBuildWriterUtil.addWrapAttribute(
                writer, "move", "tofile", "${maven.build.dir}/${maven.build.finalName}.jar", 3);
        writer.endElement(); // move

        writer.startElement("delete");
        writer.addAttribute("file", "${maven.build.dir}/bnd.bnd");
        writer.endElement(); // delete
    }

    /**
     * Whether bnd.bnd-syntax content defines the given key, line-anchored so a comment merely
     * mentioning the key (e.g. "# Bundle-Version is computed below") doesn't false-positive.
     */
    static boolean hasBndKey(String bndContent, String key) {
        if (bndContent == null) {
            return false;
        }
        for (String line : bndContent.split("\n", -1)) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("#") && !trimmed.startsWith("!") && trimmed.startsWith(key)) {
                String rest = trimmed.substring(key.length()).trim();
                if (rest.startsWith(":") || rest.startsWith("=")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Writes target/bnd.bnd: a real bnd.bnd file in the module is copied as-is (kept live rather
     * than snapshotted into the generated build); both bnd-maven-plugin's <bnd> blob and
     * maven-bundle-plugin's <instructions> are echoed as real bnd.bnd syntax (see
     * getBndPropertiesTextFromInstructions for the underscore/flag-directive/continuation handling
     * the latter needs, since XML element names can't start with '-').
     */
    private void writeBndDefinitionsFile(XMLWriter writer) {
        String bndTaskInstructions = AntBuildWriterUtil.getBndTaskInstructions(project);
        Xpp3Dom[] bundleInstructions = AntBuildWriterUtil.getBundlePluginInstructions(project);
        boolean hasBndFile = AntBuildWriterUtil.hasBndFile(project);

        boolean hasBundleVersion = false;
        if (hasBndFile) {
            try {
                File bndFile = new File(project.getBasedir(), "bnd.bnd");
                if (bndFile.isFile()) {
                    hasBundleVersion =
                            hasBndKey(org.codehaus.plexus.util.FileUtils.fileRead(bndFile), "Bundle-Version");
                }
            } catch (Exception e) {
                // ignore
            }
        }
        if (bundleInstructions != null) {
            for (Xpp3Dom entry : bundleInstructions) {
                if ("Bundle-Version".equals(entry.getName())) {
                    hasBundleVersion = true;
                    break;
                }
            }
        }
        if (hasBndKey(bndTaskInstructions, "Bundle-Version")) {
            hasBundleVersion = true;
        }

        if (hasBndFile) {
            writer.startElement("copy");
            writer.addAttribute("file", "bnd.bnd");
            writer.addAttribute("tofile", "${maven.build.dir}/bnd.bnd");
            writer.endElement(); // copy
        }

        if (bundleInstructions != null) {
            writer.startElement("echo");
            writer.addAttribute("file", "${maven.build.dir}/bnd.bnd");
            if (hasBndFile) {
                writer.addAttribute("append", "true");
            }
            writer.writeText(getBndPropertiesTextFromInstructions(bundleInstructions, hasBndFile));
            writer.endElement(); // echo
        } else if (bndTaskInstructions != null) {
            writer.startElement("echo");
            writer.addAttribute("file", "${maven.build.dir}/bnd.bnd");
            if (hasBndFile) {
                writer.addAttribute("append", "true");
            }
            writer.writeText(getBndPropertiesText(bndTaskInstructions, hasBndFile));
            writer.endElement(); // echo
        } else if (!hasBndFile) {
            writer.startElement("echo");
            writer.addAttribute("file", "${maven.build.dir}/bnd.bnd");
            writer.writeText(getBndPropertiesText((String) null, false));
            writer.endElement(); // echo
        }

        if (!hasBundleVersion) {
            String normalizedVersion = AntBuildWriterUtil.getNormalizedOSGiVersion(project.getVersion());
            writer.startElement("echo");
            writer.addAttribute("file", "${maven.build.dir}/bnd.bnd");
            writer.addAttribute("append", "true");
            writer.writeText("\nBundle-Version: " + normalizedVersion + "\n");
            writer.endElement(); // echo
        }
    }

    private String getBndPropertiesTextFromInstructions(Xpp3Dom[] bundleInstructions, boolean appending) {
        StringBuilder sb = new StringBuilder();
        if (!appending) {
            sb.append("# Generated dynamically by maven-ant-plugin\n");
        } else {
            sb.append("\n");
        }
        if (bundleInstructions != null) {
            for (Xpp3Dom entry : bundleInstructions) {
                String key = entry.getName();
                if (key.startsWith("_")) {
                    key = "-" + key.substring(1);
                }
                String value = entry.getValue() != null ? entry.getValue().trim() : "";
                if (value.isEmpty() && key.startsWith("-")) {
                    sb.append(key).append("\n");
                } else if (value.contains("\n")) {
                    String[] lines = value.split("\r?\n");
                    StringBuilder lineSb = new StringBuilder();
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            if (lineSb.length() > 0) {
                                lineSb.append(" \\\n    ");
                            }
                            lineSb.append(trimmed);
                        }
                    }
                    sb.append(key).append(": ").append(lineSb).append("\n");
                } else {
                    sb.append(key).append(": ").append(value).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String getBndPropertiesText(String instructions, boolean appending) {
        StringBuilder sb = new StringBuilder();
        if (!appending) {
            sb.append("# Generated dynamically by maven-ant-plugin\n");
        } else {
            sb.append("\n");
        }
        if (instructions != null && instructions.length() > 0) {
            sb.append(instructions).append("\n");
        }
        return sb.toString();
    }

    public void writeCompileMRTasks(
            XMLWriter writer, String outputDirectory, int intVer, List<CompilerExecution> compilerExecutions)
            throws IOException {
        int baseVersion = AntBuildWriterUtil.getBaseCompileVersion(project, compilerExecutions);
        for (CompilerExecution exec : compilerExecutions) {
            if (!AntBuildWriterUtil.isMultiReleaseExecution(exec, baseVersion, project)) {
                continue;
            }
            String ver = exec.getRelease();
            if (ver == null) {
                ver = exec.getTarget();
            }
            if (ver != null) {
                try {
                    int currentVer = (int) Double.parseDouble(ver);
                    if (currentVer == intVer && !exec.getCompileSourceRoots().isEmpty()) {
                        boolean isModuleInfo = exec.isModuleInfo(project);
                        boolean isModuleInfoOnly = false;
                        if (isModuleInfo) {
                            for (String root : exec.getCompileSourceRoots()) {
                                if (project.getCompileSourceRoots().contains(root)) {
                                    isModuleInfoOnly = true;
                                    break;
                                }
                            }
                        }

                        String mrOutputDir =
                                isModuleInfoOnly ? outputDirectory : (outputDirectory + "/META-INF/versions/" + intVer);

                        writer.startElement("mkdir");
                        writer.addAttribute("dir", mrOutputDir);
                        writer.endElement(); // mkdir

                        writer.startElement("javac");
                        writer.addAttribute("destdir", mrOutputDir);
                        writeMRJavacAttributes(writer, exec, intVer);

                        for (String root : exec.getCompileSourceRoots()) {
                            writer.startElement("src");
                            writer.startElement("pathelement");
                            writer.addAttribute("location", AntBuildWriterUtil.toRelative(project.getBasedir(), root));
                            writer.endElement(); // pathelement
                            writer.endElement(); // src
                        }

                        if (isModuleInfo) {
                            for (Object rootObj : project.getCompileSourceRoots()) {
                                String baseRoot = (String) rootObj;
                                if (!exec.getCompileSourceRoots().contains(baseRoot)) {
                                    writer.startElement("src");
                                    writer.startElement("pathelement");
                                    writer.addAttribute(
                                            "location", AntBuildWriterUtil.toRelative(project.getBasedir(), baseRoot));
                                    writer.endElement(); // pathelement
                                    writer.endElement(); // src
                                }
                            }
                        }

                        String pathTag = isModuleInfo ? "modulepath" : "classpath";
                        writer.startElement(pathTag);
                        writer.startElement("pathelement");
                        writer.addAttribute("location", outputDirectory);
                        writer.endElement(); // pathelement

                        for (CompilerExecution prevExec : compilerExecutions) {
                            String prevVerStr = prevExec.getRelease();
                            if (prevVerStr == null) {
                                prevVerStr = prevExec.getTarget();
                            }
                            if (prevVerStr != null) {
                                try {
                                    int prevVer = (int) Double.parseDouble(prevVerStr);
                                    if (prevVer > 8 && prevVer < intVer) {
                                        writer.startElement("pathelement");
                                        writer.addAttribute(
                                                "location", outputDirectory + "/META-INF/versions/" + prevVer);
                                        writer.endElement(); // pathelement
                                    }
                                } catch (NumberFormatException e) {
                                    // ignore
                                }
                            }
                        }

                        writer.startElement("path");
                        writer.addAttribute("refid", "build.classpath");
                        writer.endElement(); // path
                        writer.endElement(); // modulepath or classpath

                        writer.endElement(); // javac
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
    }

    private void writeMRJavacAttributes(XMLWriter writer, CompilerExecution exec, int intVer) throws IOException {
        AntBuildWriterUtil.addWrapAttribute(
                writer,
                "javac",
                "includeantruntime",
                AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "includeantruntime", "false"),
                3);

        if (exec.getIncludes() != null) {
            AntBuildWriterUtil.addWrapAttribute(
                    writer, "javac", "includes", getCommaSeparatedList(exec.getIncludes(), "include"), 3);
        }
        if (exec.getExcludes() != null) {
            AntBuildWriterUtil.addWrapAttribute(
                    writer, "javac", "excludes", getCommaSeparatedList(exec.getExcludes(), "exclude"), 3);
        }

        AntBuildWriterUtil.addWrapAttribute(writer, "javac", "release", String.valueOf(intVer), 3);

        AntBuildWriterUtil.addWrapAttribute(
                writer,
                "javac",
                "encoding",
                AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "encoding", null),
                3);
        AntBuildWriterUtil.addWrapAttribute(
                writer,
                "javac",
                "nowarn",
                AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "showWarnings", "false"),
                3);
        AntBuildWriterUtil.addWrapAttribute(
                writer,
                "javac",
                "debug",
                AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "debug", "true"),
                3);
        AntBuildWriterUtil.addWrapAttribute(
                writer,
                "javac",
                "optimize",
                AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "optimize", "false"),
                3);
        AntBuildWriterUtil.addWrapAttribute(
                writer,
                "javac",
                "deprecation",
                AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "showDeprecation", "true"),
                3);
    }

    private static String getCommaSeparatedList(Map[] includes, String key) {
        if ((includes == null) || (includes.length == 0)) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < includes.length; i++) {
            String s = (String) includes[i].get(key);
            if (StringUtils.isEmpty(s)) {
                continue;
            }

            sb.append(s);

            if (i < (includes.length - 1)) {
                sb.append(",");
            }
        }

        if (sb.length() == 0) {
            return null;
        }

        return sb.toString();
    }
}
