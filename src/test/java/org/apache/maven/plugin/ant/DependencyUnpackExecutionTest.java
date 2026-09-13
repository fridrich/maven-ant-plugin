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
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DependencyUnpackExecutionTest {

    @Test
    public void testNoDependencyPlugin() {
        MavenProject project = new MavenProject();
        AntExtensionWriter writer = new AntExtensionWriter(project);
        assertFalse(writer.isDependencyUnpackProject());
        assertTrue(writer.getDependencyUnpackItems().isEmpty());
    }

    @Test
    public void testExtractArtifactItemsWithFallbackOutputDir() {
        MavenProject project = new MavenProject();
        project.setFile(new File("pom.xml"));
        Build build = new Build();

        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-dependency-plugin");

        Xpp3Dom pluginConfig = new Xpp3Dom("configuration");
        Xpp3Dom topOutputDir = new Xpp3Dom("outputDirectory");
        topOutputDir.setValue("${project.build.directory}/extracted-all");
        pluginConfig.addChild(topOutputDir);
        plugin.setConfiguration(pluginConfig);

        PluginExecution exec = new PluginExecution();
        exec.setId("unpack-items");
        exec.setGoals(Collections.singletonList("unpack"));

        Xpp3Dom execConfig = new Xpp3Dom("configuration");
        Xpp3Dom itemsNode = new Xpp3Dom("artifactItems");

        // Item 1: inherits outputDirectory from plugin config, has type tar.gz
        Xpp3Dom item1 = new Xpp3Dom("artifactItem");
        addChild(item1, "groupId", "org.apache.maven");
        addChild(item1, "artifactId", "apache-maven");
        addChild(item1, "version", "3.9.9");
        addChild(item1, "type", "tar.gz");
        addChild(item1, "classifier", "bin");
        addChild(item1, "includes", "**/bin/*, **/conf/*");
        addChild(item1, "excludes", "**/*.bat");
        itemsNode.addChild(item1);

        // Item 2: explicit outputDirectory, type tar.bz2
        Xpp3Dom item2 = new Xpp3Dom("artifactItem");
        addChild(item2, "groupId", "org.example");
        addChild(item2, "artifactId", "custom-tool");
        addChild(item2, "version", "1.0.0");
        addChild(item2, "type", "tar.bz2");
        addChild(item2, "outputDirectory", "${project.build.directory}/custom-dir");
        itemsNode.addChild(item2);

        execConfig.addChild(itemsNode);
        exec.setConfiguration(execConfig);
        plugin.addExecution(exec);
        build.addPlugin(plugin);
        project.setBuild(build);

        AntExtensionWriter writer = new AntExtensionWriter(project);
        assertTrue(writer.isDependencyUnpackProject());

        List<DependencyUnpackItem> items = writer.getDependencyUnpackItems();
        assertEquals(2, items.size());

        DependencyUnpackItem parsed1 = items.get(0);
        assertEquals("org.apache.maven", parsed1.getGroupId());
        assertEquals("apache-maven", parsed1.getArtifactId());
        assertEquals("3.9.9", parsed1.getVersion());
        assertEquals("tar.gz", parsed1.getType());
        assertEquals("bin", parsed1.getClassifier());
        assertEquals("${maven.build.dir}/extracted-all", parsed1.getOutputDirectory());
        assertEquals(2, parsed1.getIncludes().size());
        assertEquals("**/bin/*", parsed1.getIncludes().get(0));
        assertEquals("**/conf/*", parsed1.getIncludes().get(1));
        assertEquals(1, parsed1.getExcludes().size());
        assertEquals("**/*.bat", parsed1.getExcludes().get(0));

        DependencyUnpackItem parsed2 = items.get(1);
        assertEquals("org.example", parsed2.getGroupId());
        assertEquals("custom-tool", parsed2.getArtifactId());
        assertEquals("1.0.0", parsed2.getVersion());
        assertEquals("tar.bz2", parsed2.getType());
        assertEquals("${maven.build.dir}/custom-dir", parsed2.getOutputDirectory());

        StringWriter sw = new StringWriter();
        PrettyPrintXMLWriter xmlWriter = new PrettyPrintXMLWriter(sw);
        writer.writeDependencyUnpackTarget(xmlWriter);
        String xml = sw.toString();

        assertTrue(xml.contains(
                "<target name=\"unpack-dependencies\" depends=\"get-deps\" description=\"Unpack dependencies\">"));
        assertTrue(
                xml.contains(
                        "<untar src=\"${maven.repo.local}/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz\""));
        assertTrue(xml.contains("dest=\"${maven.build.dir}/extracted-all\""));
        assertTrue(xml.contains("compression=\"gzip\""));
        assertTrue(xml.contains("<include name=\"**/bin/*\"/>"));
        assertTrue(xml.contains("<exclude name=\"**/*.bat\"/>"));

        assertTrue(xml.contains(
                "<untar src=\"${maven.repo.local}/org/example/custom-tool/1.0.0/custom-tool-1.0.0.tar.bz2\""));
        assertTrue(xml.contains("dest=\"${maven.build.dir}/custom-dir\""));
        assertTrue(xml.contains("compression=\"bzip2\""));
    }

    @Test
    public void testExtractUnpackDependenciesWithIncludeExcludeFilter() {
        MavenProject project = new MavenProject();
        project.setFile(new File("pom.xml"));
        Build build = new Build();

        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(createArtifact("org.jline", "jline-native", "3.30.16", "jar"));
        artifacts.add(createArtifact("org.apache.commons", "commons-lang3", "3.14.0", "jar"));
        artifacts.add(createArtifact("org.junit.jupiter", "junit-jupiter-api", "5.10.0", "jar"));
        project.setArtifacts(artifacts);

        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-dependency-plugin");

        PluginExecution exec = new PluginExecution();
        exec.setId("unpack-jline");
        exec.setGoals(Collections.singletonList("unpack-dependencies"));

        Xpp3Dom execConfig = new Xpp3Dom("configuration");
        addChild(execConfig, "includeArtifactIds", "jline-native, commons-lang3");
        addChild(execConfig, "excludeArtifactIds", "commons-lang3");
        addChild(execConfig, "outputDirectory", "${project.build.directory}/jline-out");
        exec.setConfiguration(execConfig);

        plugin.addExecution(exec);
        build.addPlugin(plugin);
        project.setBuild(build);

        AntExtensionWriter writer = new AntExtensionWriter(project);
        assertTrue(writer.isDependencyUnpackProject());

        List<DependencyUnpackItem> items = writer.getDependencyUnpackItems();
        assertEquals(1, items.size());
        assertEquals("jline-native", items.get(0).getArtifactId());
        assertEquals("${maven.build.dir}/jline-out", items.get(0).getOutputDirectory());

        StringWriter sw = new StringWriter();
        PrettyPrintXMLWriter xmlWriter = new PrettyPrintXMLWriter(sw);
        writer.writeDependencyUnpackTarget(xmlWriter);
        String xml = sw.toString();

        assertTrue(xml.contains(
                "<unjar src=\"${maven.repo.local}/org/jline/jline-native/3.30.16/jline-native-3.30.16.jar\""));
        assertTrue(xml.contains("dest=\"${maven.build.dir}/jline-out\""));
    }

    private static void addChild(Xpp3Dom parent, String name, String value) {
        Xpp3Dom child = new Xpp3Dom(name);
        child.setValue(value);
        parent.addChild(child);
    }

    private static Artifact createArtifact(String groupId, String artifactId, String version, String type) {
        return new DefaultArtifact(
                groupId,
                artifactId,
                VersionRange.createFromVersion(version),
                Artifact.SCOPE_COMPILE,
                type,
                null,
                new DefaultArtifactHandler(type));
    }
}
