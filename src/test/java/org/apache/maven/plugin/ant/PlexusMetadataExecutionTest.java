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

import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PlexusMetadataExecutionTest {

    @Test
    public void testNoPlexusPlugin() {
        MavenProject project = new MavenProject();
        AntExtensionWriter writer = new AntExtensionWriter(project);
        assertFalse(writer.isPlexusProject());
    }

    @Test
    public void testPlexusPluginInBuildPlugins() {
        MavenProject project = new MavenProject();
        project.setFile(new File("pom.xml"));
        Build build = new Build();

        Plugin plugin = new Plugin();
        plugin.setGroupId("org.codehaus.plexus");
        plugin.setArtifactId("plexus-component-metadata");
        build.addPlugin(plugin);
        project.setBuild(build);

        AntExtensionWriter writer = new AntExtensionWriter(project);
        assertTrue(writer.isPlexusProject());

        StringWriter sw = new StringWriter();
        PrettyPrintXMLWriter xmlWriter = new PrettyPrintXMLWriter(sw);
        writer.writePlexusTarget(xmlWriter, Collections.singletonList("src/main/java"));

        String output = sw.toString();
        assertTrue(output.contains("<target name=\"plexus\""));
        assertTrue(output.contains("<typedef resource=\"org/codehaus/plexus/metadata/ant/antlib.xml\"/>"));
        assertTrue(output.contains("<plexus-metadata classesDirectory=\"${maven.build.outputDir}\">"));
        assertTrue(output.contains("<sourceDirectory location=\"${maven.build.srcDir.0}\"/>"));
        assertTrue(output.contains("<classpath refid=\"build.classpath\"/>"));
    }

    @Test
    public void testPlexusPluginWithCustomConfig() {
        MavenProject project = new MavenProject();
        project.setFile(new File("pom.xml"));
        Build build = new Build();

        Plugin plugin = new Plugin();
        plugin.setGroupId("org.codehaus.plexus");
        plugin.setArtifactId("plexus-component-metadata");

        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom outputFile = new Xpp3Dom("outputFile");
        outputFile.setValue("${project.build.outputDirectory}/META-INF/plexus/components-custom.xml");
        config.addChild(outputFile);

        Xpp3Dom descriptorsDir = new Xpp3Dom("descriptorsDirectory");
        descriptorsDir.setValue("${basedir}/src/main/resources/custom");
        config.addChild(descriptorsDir);

        Xpp3Dom extractors = new Xpp3Dom("extractors");
        extractors.setValue("source,class");
        config.addChild(extractors);

        plugin.setConfiguration(config);
        build.addPlugin(plugin);
        project.setBuild(build);

        AntExtensionWriter writer = new AntExtensionWriter(project);
        assertTrue(writer.isPlexusProject());

        StringWriter sw = new StringWriter();
        PrettyPrintXMLWriter xmlWriter = new PrettyPrintXMLWriter(sw);
        writer.writePlexusTarget(xmlWriter, Collections.singletonList("src/main/java"));

        String output = sw.toString();
        assertTrue(output.contains("outputFile=\"${maven.build.outputDir}/META-INF/plexus/components-custom.xml\""));
        assertTrue(output.contains("descriptorsDirectory=\"src/main/resources/custom\""));
        assertTrue(output.contains("extractors=\"source,class\""));
    }

    @Test
    public void testPlexusPluginInheritedFromParent() {
        MavenProject parent = new MavenProject();
        Build parentBuild = new Build();
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.codehaus.plexus");
        plugin.setArtifactId("plexus-component-metadata");
        parentBuild.addPlugin(plugin);
        parent.setBuild(parentBuild);

        MavenProject child = new MavenProject();
        child.setParent(parent);
        child.setBuild(new Build());

        AntExtensionWriter writer = new AntExtensionWriter(child);
        assertTrue(writer.isPlexusProject());
    }

    @Test
    public void testPlexusPluginInPluginManagementOnly() {
        MavenProject project = new MavenProject();
        Build build = new Build();
        PluginManagement pm = new PluginManagement();
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.codehaus.plexus");
        plugin.setArtifactId("plexus-component-metadata");
        pm.addPlugin(plugin);
        build.setPluginManagement(pm);
        project.setBuild(build);

        AntExtensionWriter writer = new AntExtensionWriter(project);
        assertFalse(writer.isPlexusProject());
    }

    @Test
    public void testPlexusPluginWithInactivePhase() {
        MavenProject project = new MavenProject();
        Build build = new Build();
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.codehaus.plexus");
        plugin.setArtifactId("plexus-component-metadata");

        PluginExecution exec = new PluginExecution();
        exec.setPhase("none");
        plugin.addExecution(exec);

        build.addPlugin(plugin);
        project.setBuild(build);

        AntExtensionWriter writer = new AntExtensionWriter(project);
        assertFalse(writer.isPlexusProject());
    }

    @Test
    public void testPlexusPluginInheritingConfigFromPluginManagement() {
        MavenProject parent = new MavenProject();
        Build parentBuild = new Build();
        PluginManagement pm = new PluginManagement();
        Plugin mgmtPlugin = new Plugin();
        mgmtPlugin.setGroupId("org.codehaus.plexus");
        mgmtPlugin.setArtifactId("plexus-component-metadata");
        Xpp3Dom mgmtConfig = new Xpp3Dom("configuration");
        Xpp3Dom descDir = new Xpp3Dom("descriptorsDirectory");
        descDir.setValue("src/main/resources/custom-meta");
        mgmtConfig.addChild(descDir);
        mgmtPlugin.setConfiguration(mgmtConfig);
        pm.addPlugin(mgmtPlugin);
        parentBuild.setPluginManagement(pm);
        parent.setBuild(parentBuild);

        MavenProject child = new MavenProject();
        child.setParent(parent);
        Build childBuild = new Build();
        Plugin childPlugin = new Plugin();
        childPlugin.setGroupId("org.codehaus.plexus");
        childPlugin.setArtifactId("plexus-component-metadata");
        childBuild.addPlugin(childPlugin);
        child.setBuild(childBuild);

        AntExtensionWriter writer = new AntExtensionWriter(child);
        assertTrue(writer.isPlexusProject());

        StringWriter sw = new StringWriter();
        PrettyPrintXMLWriter xmlWriter = new PrettyPrintXMLWriter(sw);
        writer.writePlexusTarget(xmlWriter, Collections.singletonList("src/main/java"));

        String output = sw.toString();
        assertTrue(output.contains("descriptorsDirectory=\"src/main/resources/custom-meta\""));
    }
}
