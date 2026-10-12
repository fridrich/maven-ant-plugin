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
import java.util.Arrays;
import java.util.List;

import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.XMLWriter;
import org.codehaus.plexus.util.xml.XmlWriterUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Inlines maven-antrun-plugin run executions bound before compile as targets the compile depends on.
 */
class AntrunWriter {
    private static final List<String> PRE_COMPILE_PHASES = Arrays.asList(
            "validate", "initialize", "generate-sources", "process-sources", "generate-resources", "process-resources");

    private final MavenProject project;

    AntrunWriter(MavenProject project) {
        this.project = project;
    }

    /** Target names in POM order, which is also Maven's execution order within a phase. */
    List<String> getTargetNames() {
        List<String> names = new ArrayList<>();
        for (PluginExecution exec : getExecutions()) {
            names.add("antrun-" + exec.getId());
        }
        return names;
    }

    void write(XMLWriter writer) {
        for (PluginExecution exec : getExecutions()) {
            XmlWriterUtil.writeCommentText(writer, "maven-antrun-plugin execution " + exec.getId(), 1);
            writer.startElement("target");
            writer.addAttribute("name", "antrun-" + exec.getId());
            for (Xpp3Dom child : getTasks(exec).getChildren()) {
                writeNode(writer, child);
            }
            writer.endElement(); // target
            XmlWriterUtil.writeLineBreak(writer);
        }
    }

    private List<PluginExecution> getExecutions() {
        List<PluginExecution> result = new ArrayList<>();
        if (project.getBuildPlugins() == null) {
            return result;
        }
        for (Plugin plugin : project.getBuildPlugins()) {
            if ("maven-antrun-plugin".equals(plugin.getArtifactId())) {
                for (PluginExecution exec : plugin.getExecutions()) {
                    if (exec.getGoals().contains("run")
                            && PRE_COMPILE_PHASES.contains(exec.getPhase())
                            && getTasks(exec) != null) {
                        result.add(exec);
                    }
                }
            }
        }
        return result;
    }

    private static Xpp3Dom getTasks(PluginExecution exec) {
        Xpp3Dom config = (Xpp3Dom) exec.getConfiguration();
        if (config == null) {
            return null;
        }
        return config.getChild("target") != null ? config.getChild("target") : config.getChild("tasks");
    }

    private void writeNode(XMLWriter writer, Xpp3Dom node) {
        writer.startElement(node.getName());
        for (String attr : node.getAttributeNames()) {
            writer.addAttribute(attr, uninterpolate(node.getAttribute(attr)));
        }
        if (node.getChildCount() > 0) {
            for (Xpp3Dom child : node.getChildren()) {
                writeNode(writer, child);
            }
        } else if (node.getValue() != null && !node.getValue().trim().isEmpty()) {
            writer.writeText(uninterpolate(node.getValue()));
        }
        writer.endElement();
    }

    // model interpolation made ${project.build.directory}/${basedir} absolute; keep the build relocatable
    private String uninterpolate(String value) {
        String result = value;
        if (project.getBuild() != null && project.getBuild().getDirectory() != null) {
            result = result.replace(project.getBuild().getDirectory(), "${maven.build.dir}");
        }
        if (project.getBasedir() != null) {
            result = result.replace(project.getBasedir().getAbsolutePath(), "${basedir}");
        }
        return result;
    }
}
