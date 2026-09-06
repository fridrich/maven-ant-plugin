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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.XMLWriter;
import org.codehaus.plexus.util.xml.XmlWriterUtil;

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

    public void writeSisuTarget(XMLWriter writer) {
        XmlWriterUtil.writeCommentText(writer, "Sisu javax.inject.Named generation target", 1);

        writer.startElement("target");
        writer.addAttribute("name", "sisu");
        writer.addAttribute("depends", "compile");
        writer.addAttribute("description", "Generate javax.inject.Name index");

        writer.startElement("sequential");

        writer.startElement("mkdir");
        writer.addAttribute("dir", "META-INF");
        writer.endElement(); // mkdir

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

        XmlWriterUtil.writeLineBreak(writer);
    }

    public void writeBndTarget(XMLWriter writer) {
        XmlWriterUtil.writeCommentText(writer, "Bnd OSGi Bundle generation target", 1);

        writer.startElement("target");
        writer.addAttribute("name", "bnd");
        writer.addAttribute("depends", isSisuProject() ? "sisu" : "compile");
        writer.addAttribute("description", "Generate OSGi Bundle");

        writer.startElement("sequential");

        writer.startElement("taskdef");
        writer.addAttribute("resource", "aQute/bnd/ant/taskdef.properties");
        writer.addAttribute("classpathref", "build.classpath");
        writer.endElement(); // taskdef

        writer.startElement("bnd");
        writer.addAttribute("classpath", "${maven.build.outputDir}");
        writer.addAttribute("failok", "false");
        writer.addAttribute("exceptions", "true");
        writer.addAttribute("files", "bnd.bnd");
        writer.addAttribute("output", "${maven.build.dir}/${maven.build.finalName}.jar");
        writer.endElement(); // bnd

        writer.endElement(); // sequential

        writer.endElement(); // target

        XmlWriterUtil.writeLineBreak(writer);
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
