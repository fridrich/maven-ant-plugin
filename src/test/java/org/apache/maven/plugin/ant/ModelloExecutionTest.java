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
}
