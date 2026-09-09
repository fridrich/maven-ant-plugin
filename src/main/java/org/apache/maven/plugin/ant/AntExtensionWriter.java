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
import java.util.List;
import java.util.Map;

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

    private Xpp3Dom mergeConfigurations(Xpp3Dom execConfig, Xpp3Dom pluginConfig) {
        if (execConfig == null) {
            return pluginConfig;
        }
        if (pluginConfig == null) {
            return execConfig;
        }
        return Xpp3Dom.mergeXpp3Dom(execConfig, pluginConfig);
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
     * A tool's if="x.present"-gated target otherwise fails silently, surfacing as a confusing
     * error several steps later (missing generated sources, empty manifest, etc). Mirrors the
     * existing junit-missing target: warn loudly instead.
     */
    private void writeWarningBanner(XMLWriter writer, String message) {
        // CHECKSTYLE_OFF: MagicNumber
        writer.startElement("echo");
        writer.writeText(StringUtils.repeat("=", 35) + " WARNING " + StringUtils.repeat("=", 35));
        writer.endElement(); // echo

        writer.startElement("echo");
        writer.writeText(" " + message);
        writer.endElement(); // echo

        writer.startElement("echo");
        writer.writeText(StringUtils.repeat("=", 79));
        writer.endElement(); // echo
        // CHECKSTYLE_ON: MagicNumber
    }

    private void writeToolMissingWarning(XMLWriter writer, String targetName, String presentProperty, String message) {
        writer.startElement("target");
        writer.addAttribute("name", targetName);
        writer.addAttribute("unless", presentProperty);

        writeWarningBanner(writer, message);

        writer.endElement(); // target

        // plain writeLineBreak() leaves the writer's indent state broken for whatever element
        // comes right after (no writeCommentText() in between to reset it) - see writeLineBreak(3
        // args) below, which also emits the indent text itself.
        XmlWriterUtil.writeLineBreak(writer, 1, 1);
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

            writeTargetSeparator(writer, isJavaccProject() || isJflexProject() || isCupProject());
        }

        // no more if="x.present"/<fail> gating here: each of these is now a real, unconditional
        // depends= of compile/javadoc, so a missing tool fails naturally (and early) at its own
        // <taskdef>/task use, instead of several targets downstream.
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

            writeTargetSeparator(writer, isJflexProject() || isCupProject());
        }

        if (isJflexProject()) {
            writeJflexCompileTarget(writer);
        }

        if (isCupProject()) {
            writeCupCompileTarget(writer);
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

        writeTargetSeparator(writer, isCupProject());
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

        // always last in the gen-sources chain; writeCompileTarget() follows with its own comment.
        XmlWriterUtil.writeLineBreak(writer);
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
        if (value.contains("${project.build.sourceEncoding}")) {
            value = value.replace("${project.build.sourceEncoding}", "UTF-8");
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
        writer.startElement("fileset");
        writer.addAttribute("dir", "META-INF");
        writer.endElement(); // fileset
        writer.endElement(); // move

        writer.endElement(); // sequential
        writer.endElement(); // target

        // writePackageTarget() follows next with its own comment.
        XmlWriterUtil.writeLineBreak(writer);
    }

    // wraps the already-built jar; unlike <bnd> (Builder mode) it analyzes real jar content, no
    // -includeresource needed. No present/missing gating - fails naturally at <taskdef> if missing.
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
    private static boolean hasBndKey(String bndContent, String key) {
        if (bndContent == null) {
            return false;
        }
        for (String line : bndContent.split("\n", -1)) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("#") && trimmed.startsWith(key + ":")) {
                return true;
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
                java.io.File bndFile = new java.io.File(project.getBasedir(), "bnd.bnd");
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
