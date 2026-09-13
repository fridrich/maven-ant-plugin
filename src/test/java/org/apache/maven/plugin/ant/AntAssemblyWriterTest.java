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

import javax.inject.Inject;

import java.io.File;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.testing.PlexusTest;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.LocalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PlexusTest
public class AntAssemblyWriterTest {

    @Inject
    private RepositorySystem repositorySystem;

    @Test
    public void testNoAssemblyPlugin() {
        MavenProject project = new MavenProject();
        AntAssemblyWriter writer = new AntAssemblyWriter(project, null);
        assertFalse(writer.isAssemblyProject());
        assertTrue(writer.getAssemblyExecutions().isEmpty());
    }

    @Test
    public void testAssemblyPluginNoDescriptors() {
        MavenProject project = new MavenProject();
        Build build = new Build();
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-assembly-plugin");
        build.addPlugin(plugin);
        project.setBuild(build);

        AntAssemblyWriter writer = new AntAssemblyWriter(project, null);
        assertFalse(writer.isAssemblyProject());
        assertTrue(writer.getAssemblyExecutions().isEmpty());
    }

    @Test
    public void testParseAssemblyDescriptor(@TempDir Path tempDir) throws Exception {
        MavenProject project = setupAssemblyProject(tempDir);
        AntAssemblyWriter assemblyWriter = new AntAssemblyWriter(project, new AntExtensionWriter(project));

        assertTrue(assemblyWriter.isAssemblyProject());
        List<AssemblyExecution> executions = assemblyWriter.getAssemblyExecutions();
        assertEquals(1, executions.size());

        AssemblyExecution exec = executions.get(0);
        assertEquals("bin", exec.getId());
        assertEquals(3, exec.getFormats().size());
        assertTrue(exec.getFormats().contains("tar.gz"));
        assertTrue(exec.getFormats().contains("zip"));
        assertTrue(exec.getFormats().contains("dir"));
        assertTrue(exec.isIncludeBaseDirectory());

        assertEquals(1, exec.getFileSets().size());
        assertEquals("src/main/resources", exec.getFileSets().get(0).getDirectory());
        assertEquals("conf", exec.getFileSets().get(0).getOutputDirectory());

        assertEquals(1, exec.getDependencySets().size());
        assertEquals("lib", exec.getDependencySets().get(0).getOutputDirectory());
        assertFalse(exec.getDependencySets().get(0).isUseProjectArtifact());

        assertEquals(1, exec.getFiles().size());
        assertEquals("README.txt", exec.getFiles().get(0).getSource());
        assertEquals("README-DIST.txt", exec.getFiles().get(0).getDestName());
    }

    @Test
    public void testWriteAssemblyTargetXmlGeneration(@TempDir Path tempDir) throws Exception {
        MavenProject project = setupAssemblyProject(tempDir);
        AntAssemblyWriter assemblyWriter = new AntAssemblyWriter(project, new AntExtensionWriter(project));

        MavenProject sibling = new MavenProject();
        sibling.setGroupId("org.test");
        sibling.setArtifactId("submodule-a");
        sibling.setVersion("1.0.0");
        Build siblingBuild = new Build();
        siblingBuild.setFinalName("submodule-a-1.0.0");
        sibling.setBuild(siblingBuild);
        Path siblingDir = tempDir.resolve("submodule-a");
        Files.createDirectories(siblingDir);
        sibling.setFile(siblingDir.resolve("pom.xml").toFile());
        List<MavenProject> reactorProjects = Collections.singletonList(sibling);

        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(
                session, new LocalRepository(tempDir.resolve("local-repo").toFile())));
        ArtifactResolverWrapper wrapper = new ArtifactResolverWrapper(repositorySystem, session, null, null);

        StringWriter sw = new StringWriter();
        PrettyPrintXMLWriter xmlWriter = new PrettyPrintXMLWriter(sw);
        assemblyWriter.writeAssemblyTarget(xmlWriter, wrapper, reactorProjects);
        String xml = sw.toString();

        assertTrue(xml.contains(
                "<target name=\"assembly\" depends=\"compile\" description=\"Create assembly artifacts\">"));
        assertTrue(xml.contains("<mkdir dir=\"${maven.build.dir}/assembly/work\"/>"));
        assertTrue(xml.contains("<mkdir dir=\"${maven.build.dir}/assembly/work/lib\"/>"));
        assertTrue(xml.contains("<mkdir dir=\"${maven.build.dir}/assembly/work/conf\"/>"));

        // Verify sibling copy for submodule-a (from sibling target dir)
        assertTrue(
                xml.contains(
                        "<copy file=\"submodule-a/target/submodule-a-1.0.0.jar\" todir=\"${maven.build.dir}/assembly/work/lib\" failonerror=\"false\"/>"));

        // Verify repository copy for slf4j-api
        assertTrue(xml.contains("${maven.repo.local}/org/slf4j/slf4j-api/2.0.0/slf4j-api-2.0.0.jar"));

        // Verify slf4j-nop was excluded
        assertFalse(xml.contains("slf4j-nop"));

        // Verify junit was not included
        assertFalse(xml.contains("junit-4.13.2.jar"));

        // Verify file copy
        assertTrue(
                xml.contains(
                        "<copy file=\"README.txt\" tofile=\"${maven.build.dir}/assembly/work/README-DIST.txt\" failonerror=\"false\"/>"));

        // Verify tar and zip archive tasks
        assertTrue(
                xml.contains(
                        "<tar destfile=\"${maven.build.dir}/${maven.build.finalName}-bin.tar.gz\" compression=\"gzip\" longfile=\"gnu\">"));
        assertTrue(xml.contains("<zip destfile=\"${maven.build.dir}/${maven.build.finalName}-bin.zip\">"));
        assertTrue(xml.contains("prefix=\"${maven.build.finalName}\""));
    }

    private MavenProject setupAssemblyProject(Path tempDir) throws Exception {
        Path pomFile = tempDir.resolve("pom.xml");
        Files.write(pomFile, "<project/>".getBytes());

        Path descFile = tempDir.resolve("assembly-bin.xml");
        String descriptorXml = "<assembly xmlns=\"http://maven.apache.org/ASSEMBLY/2.2.0\">\n"
                + "  <id>bin</id>\n"
                + "  <formats>\n"
                + "    <format>tar.gz</format>\n"
                + "    <format>zip</format>\n"
                + "    <format>dir</format>\n"
                + "  </formats>\n"
                + "  <includeBaseDirectory>true</includeBaseDirectory>\n"
                + "  <fileSets>\n"
                + "    <fileSet>\n"
                + "      <directory>src/main/resources</directory>\n"
                + "      <outputDirectory>conf</outputDirectory>\n"
                + "      <fileMode>0644</fileMode>\n"
                + "      <lineEnding>unix</lineEnding>\n"
                + "      <includes>\n"
                + "        <include>**/*.properties</include>\n"
                + "      </includes>\n"
                + "      <excludes>\n"
                + "        <exclude>**/*.bak</exclude>\n"
                + "      </excludes>\n"
                + "    </fileSet>\n"
                + "  </fileSets>\n"
                + "  <dependencySets>\n"
                + "    <dependencySet>\n"
                + "      <outputDirectory>lib</outputDirectory>\n"
                + "      <useProjectArtifact>false</useProjectArtifact>\n"
                + "      <scope>runtime</scope>\n"
                + "      <includes>\n"
                + "        <include>:submodule-a</include>\n"
                + "        <include>org.slf4j:*</include>\n"
                + "      </includes>\n"
                + "      <excludes>\n"
                + "        <exclude>org.slf4j:slf4j-nop</exclude>\n"
                + "      </excludes>\n"
                + "    </dependencySet>\n"
                + "  </dependencySets>\n"
                + "  <files>\n"
                + "    <file>\n"
                + "      <source>README.txt</source>\n"
                + "      <outputDirectory>.</outputDirectory>\n"
                + "      <destName>README-DIST.txt</destName>\n"
                + "      <fileMode>0644</fileMode>\n"
                + "    </file>\n"
                + "  </files>\n"
                + "</assembly>";
        Files.write(descFile, descriptorXml.getBytes());

        MavenProject project = new MavenProject();
        project.setFile(pomFile.toFile());
        project.setGroupId("org.test");
        project.setArtifactId("my-project");
        project.setVersion("1.0.0");

        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(createArtifact("org.test", "submodule-a", "1.0.0", "jar", Artifact.SCOPE_COMPILE));
        artifacts.add(createArtifact("org.slf4j", "slf4j-api", "2.0.0", "jar", Artifact.SCOPE_COMPILE));
        artifacts.add(createArtifact("org.slf4j", "slf4j-nop", "2.0.0", "jar", Artifact.SCOPE_COMPILE));
        artifacts.add(createArtifact("junit", "junit", "4.13.2", "jar", Artifact.SCOPE_COMPILE));
        project.setArtifacts(artifacts);

        Build build = new Build();
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-assembly-plugin");

        Xpp3Dom pluginConfig = new Xpp3Dom("configuration");
        Xpp3Dom descriptorsNode = new Xpp3Dom("descriptors");
        Xpp3Dom dNode = new Xpp3Dom("descriptor");
        dNode.setValue("assembly-bin.xml");
        descriptorsNode.addChild(dNode);
        pluginConfig.addChild(descriptorsNode);
        plugin.setConfiguration(pluginConfig);

        build.addPlugin(plugin);
        project.setBuild(build);
        return project;
    }

    @Test
    public void testComponentDescriptorInclusion(@TempDir Path tempDir) throws Exception {
        Path pomFile = tempDir.resolve("pom.xml");
        Files.write(pomFile, "<project/>".getBytes());

        Path compFile = tempDir.resolve("component.xml");
        String compXml = "<component xmlns=\"http://maven.apache.org/ASSEMBLY/2.2.0\">\n"
                + "  <fileSets>\n"
                + "    <fileSet>\n"
                + "      <directory>src/main/docs</directory>\n"
                + "      <outputDirectory>docs</outputDirectory>\n"
                + "    </fileSet>\n"
                + "  </fileSets>\n"
                + "</component>";
        Files.write(compFile, compXml.getBytes());

        Path descFile = tempDir.resolve("assembly-with-comp.xml");
        String descXml = "<assembly xmlns=\"http://maven.apache.org/ASSEMBLY/2.2.0\">\n"
                + "  <id>dist</id>\n"
                + "  <formats>\n"
                + "    <format>zip</format>\n"
                + "  </formats>\n"
                + "  <componentDescriptors>\n"
                + "    <componentDescriptor>component.xml</componentDescriptor>\n"
                + "  </componentDescriptors>\n"
                + "</assembly>";
        Files.write(descFile, descXml.getBytes());

        MavenProject project = new MavenProject();
        project.setFile(pomFile.toFile());

        Build build = new Build();
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-assembly-plugin");

        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom dNode = new Xpp3Dom("descriptor");
        dNode.setValue("assembly-with-comp.xml");
        config.addChild(dNode);
        plugin.setConfiguration(config);
        build.addPlugin(plugin);
        project.setBuild(build);

        AntAssemblyWriter writer = new AntAssemblyWriter(project, null);
        assertTrue(writer.isAssemblyProject());
        List<AssemblyExecution> executions = writer.getAssemblyExecutions();
        assertEquals(1, executions.size());

        AssemblyExecution exec = executions.get(0);
        assertEquals(1, exec.getFileSets().size());
        assertEquals("src/main/docs", exec.getFileSets().get(0).getDirectory());
        assertEquals("docs", exec.getFileSets().get(0).getOutputDirectory());
    }

    @Test
    public void testAssemblyDependsOnUnpackDependenciesWhenPresent() {
        MavenProject project = new MavenProject();
        project.setFile(new File("pom.xml"));

        Build build = new Build();
        Plugin depPlugin = new Plugin();
        depPlugin.setGroupId("org.apache.maven.plugins");
        depPlugin.setArtifactId("maven-dependency-plugin");
        PluginExecution depExec = new PluginExecution();
        depExec.setGoals(Collections.singletonList("unpack"));
        Xpp3Dom depConfig = new Xpp3Dom("configuration");
        Xpp3Dom itemsNode = new Xpp3Dom("artifactItems");
        Xpp3Dom item = new Xpp3Dom("artifactItem");
        addChild(item, "groupId", "g");
        addChild(item, "artifactId", "a");
        addChild(item, "version", "1");
        itemsNode.addChild(item);
        depConfig.addChild(itemsNode);
        depExec.setConfiguration(depConfig);
        depPlugin.addExecution(depExec);
        build.addPlugin(depPlugin);

        Plugin assemblyPlugin = new Plugin();
        assemblyPlugin.setGroupId("org.apache.maven.plugins");
        assemblyPlugin.setArtifactId("maven-assembly-plugin");
        build.addPlugin(assemblyPlugin);
        project.setBuild(build);

        AntExtensionWriter extWriter = new AntExtensionWriter(project);
        assertTrue(extWriter.isDependencyUnpackProject());

        AntAssemblyWriter assemblyWriter = new AntAssemblyWriter(project, extWriter);

        StringWriter sw = new StringWriter();
        PrettyPrintXMLWriter xmlWriter = new PrettyPrintXMLWriter(sw);
        assemblyWriter.writeAssemblyTarget(xmlWriter, null, Collections.emptyList());
        String xml = sw.toString();

        assertTrue(xml.contains("depends=\"compile, unpack-dependencies\""));
    }

    private static void addChild(Xpp3Dom parent, String name, String value) {
        Xpp3Dom child = new Xpp3Dom(name);
        child.setValue(value);
        parent.addChild(child);
    }

    private static Artifact createArtifact(
            String groupId, String artifactId, String version, String type, String scope) {
        return new DefaultArtifact(
                groupId,
                artifactId,
                VersionRange.createFromVersion(version),
                scope,
                type,
                null,
                new DefaultArtifactHandler(type));
    }
}
