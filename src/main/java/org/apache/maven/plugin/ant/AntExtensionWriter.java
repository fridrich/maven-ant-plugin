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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            for (Object o : project.getBuildPlugins()) {
                org.apache.maven.model.Plugin plugin = (org.apache.maven.model.Plugin) o;
                if ("sisu-maven-plugin".equals(plugin.getArtifactId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getSisuVersion() {
        if (project.getDependencies() != null) {
            for (Object o : project.getDependencies()) {
                org.apache.maven.model.Dependency dep = (org.apache.maven.model.Dependency) o;
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
            for (Object o : project.getBuildPlugins()) {
                org.apache.maven.model.Plugin plugin = (org.apache.maven.model.Plugin) o;
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
        List<JflexExecution> executions = new ArrayList<JflexExecution>();
        if (project.getBuildPlugins() != null) {
            for (Object o : project.getBuildPlugins()) {
                org.apache.maven.model.Plugin plugin = (org.apache.maven.model.Plugin) o;
                if ("jflex-maven-plugin".equals(plugin.getArtifactId())) {
                    if (plugin.getExecutions() != null) {
                        for (Object execObj : plugin.getExecutions()) {
                            org.apache.maven.model.PluginExecution exec =
                                    (org.apache.maven.model.PluginExecution) execObj;
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
            for (Object o : project.getBuildPlugins()) {
                org.apache.maven.model.Plugin plugin = (org.apache.maven.model.Plugin) o;
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
        List<CupExecution> executions = new ArrayList<CupExecution>();
        if (project.getBuildPlugins() != null) {
            for (Object o : project.getBuildPlugins()) {
                org.apache.maven.model.Plugin plugin = (org.apache.maven.model.Plugin) o;
                if ("cup-maven-plugin".equals(plugin.getArtifactId())
                        || "javacup-maven-plugin".equals(plugin.getArtifactId())) {
                    if (plugin.getExecutions() != null) {
                        for (Object execObj : plugin.getExecutions()) {
                            org.apache.maven.model.PluginExecution exec =
                                    (org.apache.maven.model.PluginExecution) execObj;
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
            for (Object o : project.getBuildPlugins()) {
                org.apache.maven.model.Plugin plugin = (org.apache.maven.model.Plugin) o;
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
            for (Object o : project.getBuildPlugins()) {
                org.apache.maven.model.Plugin plugin = (org.apache.maven.model.Plugin) o;
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
            for (Object o : project.getBuildPlugins()) {
                org.apache.maven.model.Plugin plugin = (org.apache.maven.model.Plugin) o;
                if ("templating-maven-plugin".equals(plugin.getArtifactId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Extra source dirs registered via build-helper-maven-plugin's add-source goal. */
    public List<String> getAddSourceDirs() {
        List<String> dirs = new ArrayList<String>();
        if (project.getBuildPlugins() == null) {
            return dirs;
        }
        for (Object o : project.getBuildPlugins()) {
            org.apache.maven.model.Plugin plugin = (org.apache.maven.model.Plugin) o;
            if (!"build-helper-maven-plugin".equals(plugin.getArtifactId()) || plugin.getExecutions() == null) {
                continue;
            }
            for (Object execObj : plugin.getExecutions()) {
                org.apache.maven.model.PluginExecution exec = (org.apache.maven.model.PluginExecution) execObj;
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
        List<JavaccExecution> executions = new ArrayList<JavaccExecution>();
        if (project.getBuildPlugins() != null) {
            for (Object o : project.getBuildPlugins()) {
                org.apache.maven.model.Plugin plugin = (org.apache.maven.model.Plugin) o;
                if ("javacc-maven-plugin".equals(plugin.getArtifactId())
                        || "ph-javacc-maven-plugin".equals(plugin.getArtifactId())) {
                    Xpp3Dom pluginConfig = (Xpp3Dom) plugin.getConfiguration();
                    if (plugin.getExecutions() != null) {
                        for (Object execObj : plugin.getExecutions()) {
                            org.apache.maven.model.PluginExecution exec =
                                    (org.apache.maven.model.PluginExecution) execObj;
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
        List<String> files = new ArrayList<String>();
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
            return java.util.Collections.singletonList(pathOrPattern);
        }
        int lastSlash = pathOrPattern.lastIndexOf('/');
        String dir = lastSlash != -1 ? pathOrPattern.substring(0, lastSlash) : ".";
        String pattern = lastSlash != -1 ? pathOrPattern.substring(lastSlash + 1) : pathOrPattern;
        String extension = pattern.startsWith("*") ? pattern.substring(1) : pattern;

        List<String> resolved = new ArrayList<String>();
        for (String fileName : getGrammarFiles(project.getBasedir().getAbsolutePath() + "/" + dir, extension)) {
            resolved.add(dir + "/" + fileName);
        }
        return resolved;
    }

    public void writeGenSourcesTarget(XMLWriter writer) throws IOException {
        XmlWriterUtil.writeCommentText(writer, "Source generation target", 1);

        writer.startElement("target");
        writer.addAttribute("name", "gen-sources");
        writer.addAttribute("depends", "get-deps");
        writer.addAttribute("description", "Generate the sources");

        if (isTemplatingProject()) {
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
        }

        if (isJavaccProject()) {
            writer.startElement("available");
            writer.addAttribute("classname", "org.javacc.parser.Main");
            writer.addAttribute("property", "javacc.present");
            writer.addAttribute("classpathref", "build.classpath");
            writer.endElement(); // available

            writer.startElement("antcall");
            writer.addAttribute("target", "-javacc-compile");
            writer.endElement(); // antcall
        }

        if (isJflexProject()) {
            writer.startElement("available");
            writer.addAttribute("classname", "jflex.anttask.JFlexTask");
            writer.addAttribute("property", "jflex.present");
            writer.addAttribute("classpathref", "build.classpath");
            writer.endElement(); // available

            writer.startElement("antcall");
            writer.addAttribute("target", "-jflex-compile");
            writer.endElement(); // antcall
        }

        if (isCupProject()) {
            writer.startElement("available");
            writer.addAttribute("classname", "java_cup.anttask.CUPTask");
            writer.addAttribute("property", "cup.present");
            writer.addAttribute("classpathref", "build.classpath");
            writer.endElement(); // available

            writer.startElement("antcall");
            writer.addAttribute("target", "-cup-compile");
            writer.endElement(); // antcall
        }

        writer.endElement(); // target

        XmlWriterUtil.writeLineBreak(writer);

        if (isJavaccProject()) {
            writer.startElement("target");
            writer.addAttribute("name", "-javacc-compile");
            writer.addAttribute("if", "javacc.present");

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

            XmlWriterUtil.writeLineBreak(writer);
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
        writer.addAttribute("name", "-jflex-compile");
        writer.addAttribute("if", "jflex.present");

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

            List<String> lexFiles = new ArrayList<String>();
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
                    writer.addAttribute("file", resolved);
                    writer.addAttribute("destdir", outputDirectory);
                    writer.endElement(); // jflex
                }
            }
        }

        writer.endElement(); // sequential
        writer.endElement(); // target

        XmlWriterUtil.writeLineBreak(writer);
    }

    public void writeCupCompileTarget(XMLWriter writer) throws IOException {
        writer.startElement("target");
        writer.addAttribute("name", "-cup-compile");
        writer.addAttribute("if", "cup.present");

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

            List<String> cupFiles = new ArrayList<String>();
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
                    writer.addAttribute("srcfile", resolved);
                    writer.addAttribute("destdir", outputDirectory);
                    writer.addAttribute("interface", "true");
                    writer.endElement(); // cup
                }
            }
        }

        writer.endElement(); // sequential
        writer.endElement(); // target

        XmlWriterUtil.writeLineBreak(writer);
    }

    private void writeJjtreeTask(XMLWriter writer, String sourceDirectory, String outputDirectory, Xpp3Dom config)
            throws IOException {
        List<String> includes = new ArrayList<String>();
        String includeOption = getIncludeFile(config, null);
        if (includeOption != null) {
            includes.add(includeOption);
        } else {
            includes.addAll(getGrammarFiles(project.getBasedir().getAbsolutePath() + "/" + sourceDirectory, ".jjt"));
        }

        if (!includes.isEmpty()) {
            writer.startElement("copy");
            writer.addAttribute("file", "${maven.repo.local}/net/java/dev/javacc/javacc/7.0.12/javacc-7.0.12.jar");
            writer.addAttribute("tofile", "${maven.repo.local}/net/java/dev/javacc/javacc/7.0.12/javacc.jar");
            writer.addAttribute("preservelastmodified", "true");
            writer.addAttribute("failonerror", "false");
            writer.endElement(); // copy
        }

        for (String include : includes) {
            writer.startElement("jjtree");
            writer.addAttribute("target", sourceDirectory + "/" + include);
            writer.addAttribute("outputdirectory", outputDirectory);
            writer.addAttribute("javacchome", "${maven.repo.local}/net/java/dev/javacc/javacc/7.0.12");
            writer.addAttribute("static", getOption(config, "isStatic", "false"));
            writer.addAttribute("multi", getOption(config, "multi", "true"));
            String nodePackage = getOption(config, "nodePackage", null);
            if (nodePackage != null) {
                writer.addAttribute("nodepackage", nodePackage);
            }
            writer.addAttribute("nodeusesparser", getOption(config, "nodeUsesParser", "true"));
            writer.addAttribute("buildnodefiles", getOption(config, "buildNodeFiles", "false"));
            writer.endElement(); // jjtree
        }
    }

    private void writeJavaccTask(
            XMLWriter writer, String sourceDirectory, String outputDirectory, Xpp3Dom config, String goal)
            throws IOException {
        List<String> includes = new ArrayList<String>();
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
            writer.addAttribute("file", "${maven.repo.local}/net/java/dev/javacc/javacc/7.0.12/javacc-7.0.12.jar");
            writer.addAttribute("tofile", "${maven.repo.local}/net/java/dev/javacc/javacc/7.0.12/javacc.jar");
            writer.addAttribute("preservelastmodified", "true");
            writer.addAttribute("failonerror", "false");
            writer.endElement(); // copy
        }

        for (String include : includes) {
            writer.startElement("javacc");
            String inputDir =
                    "jjtree-javacc".equals(goal) ? "${maven.build.dir}/generated-sources/jjtree" : sourceDirectory;
            writer.addAttribute("target", inputDir + "/" + include);
            writer.addAttribute("outputdirectory", outputDirectory);
            writer.addAttribute("javacchome", "${maven.repo.local}/net/java/dev/javacc/javacc/7.0.12");
            writer.addAttribute("static", getOption(config, "isStatic", "false"));
            writer.addAttribute("buildparser", getOption(config, "buildParser", "true"));
            writer.addAttribute("debugparser", getOption(config, "debugParser", "false"));
            writer.addAttribute("debuglookahead", getOption(config, "debugLookAhead", "false"));
            writer.addAttribute("debugtokenmanager", getOption(config, "debugTokenManager", "false"));
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

        writer.startElement("available");
        writer.addAttribute("classname", "org.eclipse.sisu.space.SisuIndex");
        writer.addAttribute("property", "sisu.present");
        writer.addAttribute("classpathref", "build.classpath");
        writer.endElement(); // available

        writer.startElement("antcall");
        writer.addAttribute("target", "sisu-index");
        writer.endElement(); // antcall

        writer.endElement(); // sequential
        writer.endElement(); // target

        writer.startElement("target");
        writer.addAttribute("name", "sisu-index");
        writer.addAttribute("if", "sisu.present");

        writer.startElement("sequential");

        writer.startElement("mkdir");
        writer.addAttribute("dir", "META-INF");
        writer.endElement(); // mkdir

        writer.startElement("java");
        writer.addAttribute("classname", "org.eclipse.sisu.space.SisuIndex");
        writer.addAttribute("failonerror", "false");
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

        XmlWriterUtil.writeLineBreak(writer);
    }

    public void writeBndTarget(XMLWriter writer) {
        XmlWriterUtil.writeCommentText(writer, "Bnd OSGi manifest generation target", 1);

        writer.startElement("target");
        writer.addAttribute("name", "-bnd");
        writer.addAttribute("depends", "compile");
        writer.addAttribute("description", "Generate the OSGi manifest");

        writer.startElement("sequential");

        writer.startElement("available");
        writer.addAttribute("resource", "aQute/bnd/ant/taskdef.properties");
        writer.addAttribute("property", "bnd.present");
        writer.addAttribute("classpathref", "build.classpath");
        writer.endElement(); // available

        writer.startElement("antcall");
        writer.addAttribute("target", "-bnd-bundle");
        writer.endElement(); // antcall

        writer.endElement(); // sequential
        writer.endElement(); // target

        // bnd always builds a full jar (Builder mode); only its MANIFEST.MF is kept, the real
        // <jar> task packs the classes - mirrors bnd-maven-plugin's bnd-process goal.
        writer.startElement("target");
        writer.addAttribute("name", "-bnd-bundle");
        writer.addAttribute("if", "bnd.present");

        writer.startElement("sequential");

        writer.startElement("echo");
        writer.addAttribute("file", "${maven.build.dir}/bnd.bnd");
        writer.writeText(getBndPropertiesText());
        writer.endElement(); // echo

        writer.startElement("taskdef");
        writer.addAttribute("resource", "aQute/bnd/ant/taskdef.properties");
        writer.addAttribute("classpathref", "build.classpath");
        writer.endElement(); // taskdef

        writer.startElement("bnd");
        writer.addAttribute("classpath", "${maven.build.outputDir}");
        writer.addAttribute("failok", "false");
        writer.addAttribute("exceptions", "true");
        writer.addAttribute("files", "${maven.build.dir}/bnd.bnd");
        writer.addAttribute("output", "${maven.build.dir}/bnd-manifest.jar");
        writer.endElement(); // bnd

        writer.startElement("mkdir");
        writer.addAttribute("dir", "${maven.build.outputDir}/META-INF");
        writer.endElement(); // mkdir

        writer.startElement("unzip");
        writer.addAttribute("src", "${maven.build.dir}/bnd-manifest.jar");
        writer.addAttribute("dest", "${maven.build.outputDir}");
        writer.startElement("patternset");
        writer.startElement("include");
        writer.addAttribute("name", "META-INF/MANIFEST.MF");
        writer.endElement(); // include
        writer.endElement(); // patternset
        writer.endElement(); // unzip

        writer.startElement("delete");
        writer.addAttribute("file", "${maven.build.dir}/bnd.bnd");
        writer.endElement(); // delete

        writer.startElement("delete");
        writer.addAttribute("file", "${maven.build.dir}/bnd-manifest.jar");
        writer.endElement(); // delete

        writer.endElement(); // sequential
        writer.endElement(); // target

        XmlWriterUtil.writeLineBreak(writer);
    }

    private String getBndPropertiesText() {
        String instructions = AntBuildWriterUtil.getBndInstructions(project);
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated dynamically by maven-ant-plugin\n");
        if (instructions != null && instructions.length() > 0) {
            sb.append(instructions).append("\n");
        }
        // -exportcontents alone won't pull classes into the jar; force them in. Must be an absolute
        // path: bnd resolves "@path" against its own basedir, not the ant project basedir.
        if (instructions == null || !instructions.contains("-includeresource")) {
            String outputDir = new File(project.getBuild().getOutputDirectory())
                    .getAbsolutePath()
                    .replace('\\', '/');
            sb.append("-includeresource: @").append(outputDir).append("\n");
        }
        return sb.toString();
    }

    public void writeCompileMRTasks(
            XMLWriter writer, String outputDirectory, int intVer, List<CompilerExecution> compilerExecutions)
            throws IOException {
        for (CompilerExecution exec : compilerExecutions) {
            String ver = exec.getRelease();
            if (ver == null) {
                ver = exec.getTarget();
            }
            if (ver != null) {
                try {
                    int currentVer = (int) Double.parseDouble(ver);
                    if (currentVer == intVer && !exec.getCompileSourceRoots().isEmpty()) {
                        boolean isModuleInfoOnly = false;
                        for (String root : exec.getCompileSourceRoots()) {
                            if (project.getCompileSourceRoots().contains(root)) {
                                isModuleInfoOnly = true;
                                break;
                            }
                        }

                        String mrOutputDir =
                                isModuleInfoOnly ? outputDirectory : (outputDirectory + "/META-INF/versions/" + intVer);

                        writer.startElement("mkdir");
                        writer.addAttribute("dir", mrOutputDir);
                        writer.endElement(); // mkdir

                        writer.startElement("javac");
                        writer.addAttribute("destdir", mrOutputDir);

                        if (exec.getIncludes() != null) {
                            AntBuildWriterUtil.addWrapAttribute(
                                    writer,
                                    "javac",
                                    "includes",
                                    getCommaSeparatedList(exec.getIncludes(), "include"),
                                    3);
                        }
                        if (exec.getExcludes() != null) {
                            AntBuildWriterUtil.addWrapAttribute(
                                    writer,
                                    "javac",
                                    "excludes",
                                    getCommaSeparatedList(exec.getExcludes(), "exclude"),
                                    3);
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
                                AntBuildWriterUtil.getMavenCompilerPluginBasicOption(
                                        project, "showDeprecation", "true"),
                                3);

                        boolean isModuleInfo = exec.isModuleInfo(project);

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
