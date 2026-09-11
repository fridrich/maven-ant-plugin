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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ModelloExecutionTest {

    @Test
    public void testDomAsXpp3DefaultsAndAccessors() {
        ModelloExecution exec = new ModelloExecution("id", "1.0.0", "target", "8");
        assertTrue(exec.isDomAsXpp3());

        exec.setDomAsXpp3(false);
        assertFalse(exec.isDomAsXpp3());

        ModelloExecution copy = new ModelloExecution(exec);
        assertFalse(copy.isDomAsXpp3());

        ModelloExecution other = new ModelloExecution("id", "1.0.0", "target", "8");
        other.setDomAsXpp3(true);
        assertFalse(exec.canMergeWith(other));

        other.setDomAsXpp3(false);
        assertTrue(exec.canMergeWith(other));
    }

    @Test
    public void testAntExtensionWriterDomAsXpp3False() throws Exception {
        MavenProject project = new MavenProject();
        project.setFile(new File("pom.xml"));
        Build build = new Build();

        Plugin plugin = new Plugin();
        plugin.setGroupId("org.codehaus.modello");
        plugin.setArtifactId("modello-maven-plugin");

        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom versionNode = new Xpp3Dom("version");
        versionNode.setValue("1.0.0");
        config.addChild(versionNode);
        Xpp3Dom domAsXpp3Node = new Xpp3Dom("domAsXpp3");
        domAsXpp3Node.setValue("false");
        config.addChild(domAsXpp3Node);
        Xpp3Dom modelsNode = new Xpp3Dom("models");
        Xpp3Dom modelNode = new Xpp3Dom("model");
        modelNode.setValue("src/main/mdo/test.mdo");
        modelsNode.addChild(modelNode);
        config.addChild(modelsNode);
        plugin.setConfiguration(config);

        PluginExecution execution = new PluginExecution();
        execution.setId("modello");
        execution.setGoals(Collections.singletonList("java"));
        plugin.addExecution(execution);

        build.addPlugin(plugin);
        project.setBuild(build);

        AntExtensionWriter writer = new AntExtensionWriter(project);
        assertTrue(writer.isModelloProject());
        assertEquals(1, writer.getMergedModelloExecutions().size());
        assertFalse(writer.getMergedModelloExecutions().get(0).isDomAsXpp3());

        StringWriter sw = new StringWriter();
        PrettyPrintXMLWriter xmlWriter = new PrettyPrintXMLWriter(sw);
        writer.writeModelloTarget(xmlWriter);

        String output = sw.toString();
        assertTrue(output.contains("domAsXpp3=\"false\""));
    }

    @Test
    public void testInheritExecutionsFromPluginManagement() {
        MavenProject project = new MavenProject();
        project.setFile(new File("pom.xml"));
        Build build = new Build();

        Plugin plugin = new Plugin();
        plugin.setGroupId("org.codehaus.modello");
        plugin.setArtifactId("modello-maven-plugin");
        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom versionNode = new Xpp3Dom("version");
        versionNode.setValue("1.2.0");
        config.addChild(versionNode);
        Xpp3Dom modelsNode = new Xpp3Dom("models");
        Xpp3Dom modelNode = new Xpp3Dom("model");
        modelNode.setValue("src/main/mdo/settings.mdo");
        modelsNode.addChild(modelNode);
        config.addChild(modelsNode);
        plugin.setConfiguration(config);
        build.addPlugin(plugin);

        PluginManagement pm = new PluginManagement();
        Plugin mgmtPlugin = new Plugin();
        mgmtPlugin.setGroupId("org.codehaus.modello");
        mgmtPlugin.setArtifactId("modello-maven-plugin");
        PluginExecution exec = new PluginExecution();
        exec.setId("modello");
        exec.setGoals(Arrays.asList("java", "xpp3-reader", "xpp3-writer"));
        mgmtPlugin.addExecution(exec);
        pm.addPlugin(mgmtPlugin);
        build.setPluginManagement(pm);

        project.setBuild(build);

        AntExtensionWriter writer = new AntExtensionWriter(project);
        assertTrue(writer.isModelloProject());
        List<ModelloExecution> execs = writer.getMergedModelloExecutions();
        assertEquals(1, execs.size());
        ModelloExecution mExec = execs.get(0);
        assertEquals("1.2.0", mExec.getVersion());
        assertEquals(Collections.singletonList("src/main/mdo/settings.mdo"), mExec.getModels());
        assertEquals(Arrays.asList("java", "xpp3-reader", "xpp3-writer"), mExec.getGoals());
    }

    @Test
    public void testInheritExecutionsFromParentProject() {
        MavenProject parent = new MavenProject();
        parent.setFile(new File("../pom.xml"));
        Build parentBuild = new Build();
        PluginManagement pm = new PluginManagement();
        Plugin mgmtPlugin = new Plugin();
        mgmtPlugin.setGroupId("org.codehaus.modello");
        mgmtPlugin.setArtifactId("modello-maven-plugin");

        PluginExecution siteExec = new PluginExecution();
        siteExec.setId("modello-site-docs");
        siteExec.setPhase("pre-site");
        siteExec.setGoals(Arrays.asList("xdoc", "xsd"));
        mgmtPlugin.addExecution(siteExec);

        PluginExecution codeExec = new PluginExecution();
        codeExec.setId("modello");
        codeExec.setGoals(Arrays.asList("java", "xpp3-reader", "xpp3-writer"));
        mgmtPlugin.addExecution(codeExec);

        pm.addPlugin(mgmtPlugin);
        parentBuild.setPluginManagement(pm);
        parent.setBuild(parentBuild);

        MavenProject child = new MavenProject();
        child.setFile(new File("pom.xml"));
        child.setParent(parent);
        Build childBuild = new Build();
        Plugin childPlugin = new Plugin();
        childPlugin.setGroupId("org.codehaus.modello");
        childPlugin.setArtifactId("modello-maven-plugin");
        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom versionNode = new Xpp3Dom("version");
        versionNode.setValue("1.1.0");
        config.addChild(versionNode);
        Xpp3Dom modelsNode = new Xpp3Dom("models");
        Xpp3Dom modelNode = new Xpp3Dom("model");
        modelNode.setValue("src/main/mdo/toolchains.mdo");
        modelsNode.addChild(modelNode);
        config.addChild(modelsNode);
        childPlugin.setConfiguration(config);

        PluginExecution childSiteExec = new PluginExecution();
        childSiteExec.setId("modello-site-doc");
        childSiteExec.setPhase("pre-site");
        childSiteExec.setGoals(Collections.singletonList("xdoc"));
        childPlugin.addExecution(childSiteExec);

        childBuild.addPlugin(childPlugin);
        child.setBuild(childBuild);

        AntExtensionWriter writer = new AntExtensionWriter(child);
        assertTrue(writer.isModelloProject());
        List<ModelloExecution> execs = writer.getMergedModelloExecutions();
        assertEquals(1, execs.size());
        ModelloExecution mExec = execs.get(0);
        assertEquals("1.1.0", mExec.getVersion());
        assertEquals(Collections.singletonList("src/main/mdo/toolchains.mdo"), mExec.getModels());
        assertEquals(Arrays.asList("java", "xpp3-reader", "xpp3-writer"), mExec.getGoals());
    }

    @Test
    public void testChildOverridesParentExecution() {
        MavenProject parent = new MavenProject();
        parent.setFile(new File("../pom.xml"));
        Build parentBuild = new Build();
        PluginManagement pm = new PluginManagement();
        Plugin mgmtPlugin = new Plugin();
        mgmtPlugin.setGroupId("org.codehaus.modello");
        mgmtPlugin.setArtifactId("modello-maven-plugin");
        PluginExecution parentExec = new PluginExecution();
        parentExec.setId("modello");
        parentExec.setGoals(Arrays.asList("java", "xpp3-reader", "xpp3-writer"));
        mgmtPlugin.addExecution(parentExec);
        pm.addPlugin(mgmtPlugin);
        parentBuild.setPluginManagement(pm);
        parent.setBuild(parentBuild);

        MavenProject child = new MavenProject();
        child.setFile(new File("pom.xml"));
        child.setParent(parent);
        Build childBuild = new Build();
        Plugin childPlugin = new Plugin();
        childPlugin.setGroupId("org.codehaus.modello");
        childPlugin.setArtifactId("modello-maven-plugin");
        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom versionNode = new Xpp3Dom("version");
        versionNode.setValue("1.0.0");
        config.addChild(versionNode);
        childPlugin.setConfiguration(config);

        PluginExecution childExec = new PluginExecution();
        childExec.setId("modello");
        childExec.setGoals(Collections.singletonList("java"));
        childPlugin.addExecution(childExec);

        childBuild.addPlugin(childPlugin);
        child.setBuild(childBuild);

        AntExtensionWriter writer = new AntExtensionWriter(child);
        List<ModelloExecution> execs = writer.getMergedModelloExecutions();
        assertEquals(1, execs.size());
        assertEquals(Collections.singletonList("java"), execs.get(0).getGoals());
    }

    @Test
    public void testChildDisablesParentExecutionWithPhaseNone() {
        MavenProject parent = new MavenProject();
        parent.setFile(new File("../pom.xml"));
        Build parentBuild = new Build();
        PluginManagement pm = new PluginManagement();
        Plugin mgmtPlugin = new Plugin();
        mgmtPlugin.setGroupId("org.codehaus.modello");
        mgmtPlugin.setArtifactId("modello-maven-plugin");
        PluginExecution parentExec = new PluginExecution();
        parentExec.setId("modello");
        parentExec.setGoals(Arrays.asList("java", "xpp3-reader", "xpp3-writer"));
        mgmtPlugin.addExecution(parentExec);
        pm.addPlugin(mgmtPlugin);
        parentBuild.setPluginManagement(pm);
        parent.setBuild(parentBuild);

        MavenProject child = new MavenProject();
        child.setFile(new File("pom.xml"));
        child.setParent(parent);
        Build childBuild = new Build();
        Plugin childPlugin = new Plugin();
        childPlugin.setGroupId("org.codehaus.modello");
        childPlugin.setArtifactId("modello-maven-plugin");

        PluginExecution childExec = new PluginExecution();
        childExec.setId("modello");
        childExec.setPhase("none");
        childPlugin.addExecution(childExec);

        childBuild.addPlugin(childPlugin);
        child.setBuild(childBuild);

        AntExtensionWriter writer = new AntExtensionWriter(child);
        assertFalse(writer.isModelloProject());
        assertTrue(writer.getMergedModelloExecutions().isEmpty());
    }
}
