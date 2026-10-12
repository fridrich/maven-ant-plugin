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
import java.util.ArrayList;
import java.util.Arrays;
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
import org.codehaus.plexus.util.xml.XMLWriter;
import org.codehaus.plexus.util.xml.XmlWriterUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * maven-dependency-plugin unpack / unpack-dependencies executions as an Ant target.
 */
class DependencyUnpackWriter {
    private final MavenProject project;
    private final boolean standalone;
    private final File rootProjectDir;
    private final List<MavenProject> reactorProjects;

    DependencyUnpackWriter(
            MavenProject project, boolean standalone, File rootProjectDir, List<MavenProject> reactorProjects) {
        this.project = project;
        this.standalone = standalone;
        this.rootProjectDir = rootProjectDir;
        this.reactorProjects = reactorProjects;
    }

    void write(XMLWriter writer) {
        List<DependencyUnpackItem> items = getItems();
        if (items.isEmpty()) {
            return;
        }

        XmlWriterUtil.writeCommentText(writer, "Unpack dependencies target", 1);
        writer.startElement("target");
        writer.addAttribute("name", "unpack-dependencies");
        if (!standalone) {
            writer.addAttribute("depends", "get-deps");
        }
        writer.addAttribute("description", "Unpack dependencies");

        for (DependencyUnpackItem item : items) {
            String outDir =
                    item.getOutputDirectory() != null ? item.getOutputDirectory() : "${maven.build.dir}/dependency";
            writer.startElement("mkdir");
            writer.addAttribute("dir", outDir);
            writer.endElement(); // mkdir

            if (!item.getSourceDirs().isEmpty()) {
                writer.startElement("copy");
                writer.addAttribute("todir", outDir);
                for (String dir : item.getSourceDirs()) {
                    writer.startElement("fileset");
                    writer.addAttribute("dir", dir);
                    writePatterns(writer, item);
                    writer.endElement(); // fileset
                }
                writer.endElement(); // copy
                continue;
            }

            boolean isTar = Arrays.asList("tar.gz", "tgz", "tar.bz2", "tar").contains(item.getType());
            writer.startElement(isTar ? "untar" : "unjar");
            if (!standalone) {
                String relPath = getArtifactPath(
                        item.getGroupId(),
                        item.getArtifactId(),
                        item.getVersion(),
                        item.getType(),
                        item.getClassifier());
                writer.addAttribute("src", "${maven.repo.local}/" + relPath);
            }
            writer.addAttribute("dest", outDir);
            if ("tar.gz".equals(item.getType()) || "tgz".equals(item.getType())) {
                writer.addAttribute("compression", "gzip");
            } else if ("tar.bz2".equals(item.getType())) {
                writer.addAttribute("compression", "bzip2");
            }

            if (standalone) {
                writer.startElement("fileset");
                writer.addAttribute("dir", ReactorPaths.libRelPath(project, rootProjectDir));
                String ext = extension(item.getType());
                boolean hasClassifier =
                        item.getClassifier() != null && !item.getClassifier().isEmpty();
                if (hasClassifier) {
                    writePattern(
                            writer, "include", "*" + item.getArtifactId() + "*" + item.getClassifier() + "*." + ext);
                } else {
                    writePattern(writer, "include", "*" + item.getArtifactId() + "*." + ext);
                    writePattern(writer, "exclude", "*" + item.getArtifactId() + "*sources*." + ext);
                    writePattern(writer, "exclude", "*" + item.getArtifactId() + "*tests*." + ext);
                }
                writer.endElement(); // fileset
            }

            if (!item.getIncludes().isEmpty() || !item.getExcludes().isEmpty()) {
                writer.startElement("patternset");
                writePatterns(writer, item);
                writer.endElement(); // patternset
            }

            writer.endElement(); // untar or unjar
        }

        writer.endElement(); // target

        XmlWriterUtil.writeLineBreak(writer);
    }

    List<DependencyUnpackItem> getItems() {
        List<DependencyUnpackItem> items = new ArrayList<>();
        if (project == null || project.getBuildPlugins() == null) {
            return items;
        }
        for (Plugin plugin : project.getBuildPlugins()) {
            if ("maven-dependency-plugin".equals(plugin.getArtifactId()) && plugin.getExecutions() != null) {
                Xpp3Dom pluginConfig = (Xpp3Dom) plugin.getConfiguration();
                for (PluginExecution exec : plugin.getExecutions()) {
                    if (exec.getGoals() == null) {
                        continue;
                    }
                    Xpp3Dom config =
                            AntExtensionWriter.mergeConfigurations((Xpp3Dom) exec.getConfiguration(), pluginConfig);
                    if (exec.getGoals().contains("unpack")) {
                        items.addAll(extractArtifactItems(config));
                    }
                    if (exec.getGoals().contains("unpack-dependencies")) {
                        items.addAll(extractUnpackDependenciesItems(config));
                    }
                }
            }
        }
        return items;
    }

    private List<DependencyUnpackItem> extractArtifactItems(Xpp3Dom config) {
        List<DependencyUnpackItem> result = new ArrayList<>();
        Xpp3Dom itemsNode = config != null ? config.getChild("artifactItems") : null;
        if (itemsNode == null) {
            return result;
        }
        for (Xpp3Dom itemNode : itemsNode.getChildren("artifactItem")) {
            DependencyUnpackItem item = new DependencyUnpackItem();
            item.setGroupId(getChildValue(itemNode, "groupId"));
            item.setArtifactId(getChildValue(itemNode, "artifactId"));
            String ver = getChildValue(itemNode, "version");
            item.setVersion(
                    (ver != null && !ver.isEmpty())
                            ? ver
                            : resolveDependencyVersion(item.getGroupId(), item.getArtifactId()));
            item.setType(getChildValue(itemNode, "type"));
            item.setClassifier(getChildValue(itemNode, "classifier"));
            String od = getChildValue(itemNode, "outputDirectory");
            if (od == null) {
                od = getChildValue(config, "outputDirectory");
            }
            if (od != null) {
                item.setOutputDirectory(toAntOutputDir(od));
            }
            item.getIncludes().addAll(parseCsv(itemNode.getChild("includes"), "[,\\n\\r]+"));
            item.getExcludes().addAll(parseCsv(itemNode.getChild("excludes"), "[,\\n\\r]+"));
            if (item.getGroupId() != null && item.getArtifactId() != null && item.getVersion() != null) {
                result.add(item);
            }
        }
        return result;
    }

    private List<DependencyUnpackItem> extractUnpackDependenciesItems(Xpp3Dom config) {
        if (config == null || project.getArtifacts() == null) {
            return new ArrayList<>();
        }
        Set<String> inc = new HashSet<>(parseCsv(config.getChild("includeArtifactIds"), "[,\\s]+"));
        Set<String> exc = new HashSet<>(parseCsv(config.getChild("excludeArtifactIds"), "[,\\s]+"));
        boolean excludeTransitive = "true".equals(getChildValue(config, "excludeTransitive"));
        // classifier/type in config replace the resolved artifact's (e.g. "sources" of each dependency)
        String classifier = getChildValue(config, "classifier");
        String type = getChildValue(config, "type");
        String outDir = config.getChild("outputDirectory") != null
                ? toAntOutputDir(config.getChild("outputDirectory").getValue())
                : "${maven.build.dir}/dependency";
        // keyed by GA + classifier: main and test-jar of one dependency collapse once classifier is overridden
        Map<String, DependencyUnpackItem> result = new LinkedHashMap<>();
        for (Artifact a : project.getArtifacts()) {
            if ((!inc.isEmpty() && !inc.contains(a.getArtifactId())) || exc.contains(a.getArtifactId())) {
                continue;
            }
            if (excludeTransitive
                    && a.getDependencyTrail() != null
                    && a.getDependencyTrail().size() > 2) {
                continue;
            }
            DependencyUnpackItem item = new DependencyUnpackItem();
            item.setGroupId(a.getGroupId());
            item.setArtifactId(a.getArtifactId());
            item.setVersion(a.getVersion());
            item.setType(type != null ? type : (a.getType() != null ? a.getType() : "jar"));
            item.setClassifier(classifier != null ? classifier : a.getClassifier());
            item.setOutputDirectory(outDir);
            item.getIncludes().addAll(parseCsv(config.getChild("includes"), "[,\\s]+"));
            item.getExcludes().addAll(parseCsv(config.getChild("excludes"), "[,\\s]+"));
            MavenProject sibling = AntBuildWriterUtil.findReactorProject(a, reactorProjects);
            if (sibling != null) {
                item.getSourceDirs().addAll(ReactorPaths.siblingDirs(project, sibling, item.getClassifier()));
            }
            result.putIfAbsent(a.getGroupId() + ":" + a.getArtifactId() + ":" + item.getClassifier(), item);
        }
        return new ArrayList<>(result.values());
    }

    private static List<String> parseCsv(Xpp3Dom node, String separators) {
        List<String> list = new ArrayList<>();
        if (node != null && node.getValue() != null) {
            for (String s : node.getValue().split(separators)) {
                if (!s.trim().isEmpty()) {
                    list.add(s.trim());
                }
            }
        }
        return list;
    }

    private String resolveDependencyVersion(String groupId, String artifactId) {
        if (groupId == null || artifactId == null) {
            return null;
        }
        List<Dependency> deps = new ArrayList<>();
        if (project.getDependencies() != null) {
            deps.addAll(project.getDependencies());
        }
        if (project.getDependencyManagement() != null
                && project.getDependencyManagement().getDependencies() != null) {
            deps.addAll(project.getDependencyManagement().getDependencies());
        }
        for (Dependency dep : deps) {
            if (groupId.equals(dep.getGroupId())
                    && artifactId.equals(dep.getArtifactId())
                    && dep.getVersion() != null) {
                return dep.getVersion();
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
        // model interpolation usually already turned the above into absolute paths
        String buildDir = project.getBuild() != null ? project.getBuild().getDirectory() : null;
        if (buildDir != null && replaced.startsWith(buildDir)) {
            replaced = "${maven.build.dir}" + replaced.substring(buildDir.length());
        } else if (project.getBasedir() != null && new File(replaced).isAbsolute()) {
            replaced = AntBuildWriterUtil.toRelative(project.getBasedir(), replaced);
        }
        return replaced;
    }

    static String getArtifactPath(String groupId, String artifactId, String version, String type, String classifier) {
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
        sb.append('.').append(extension(type));
        return sb.toString();
    }

    // artifact handler types whose file is a plain jar
    private static String extension(String type) {
        return type == null || "java-source".equals(type) || "test-jar".equals(type) ? "jar" : type;
    }

    private static void writePatterns(XMLWriter writer, DependencyUnpackItem item) {
        for (String inc : item.getIncludes()) {
            writePattern(writer, "include", inc);
        }
        for (String exc : item.getExcludes()) {
            writePattern(writer, "exclude", exc);
        }
    }

    private static void writePattern(XMLWriter writer, String tag, String name) {
        writer.startElement(tag);
        writer.addAttribute("name", name);
        writer.endElement();
    }

    private static String getChildValue(Xpp3Dom node, String child) {
        Xpp3Dom c = node != null ? node.getChild(child) : null;
        return (c != null && c.getValue() != null) ? c.getValue().trim() : null;
    }
}
