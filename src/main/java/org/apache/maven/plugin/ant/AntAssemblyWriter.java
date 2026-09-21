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
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.XMLWriter;
import org.codehaus.plexus.util.xml.XmlWriterUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Generates Ant targets for maven-assembly-plugin executions.
 *
 * // ponytail: Generates work staging directory and archive targets for assembly descriptors.
 * // Upgrade path: full Plexus Assembly Reader if external component interpolation is needed.
 */
public class AntAssemblyWriter {

    private final MavenProject project;
    private final AntExtensionWriter extensionWriter;
    private List<AssemblyExecution> assemblyExecutions;

    public AntAssemblyWriter(MavenProject project, AntExtensionWriter extensionWriter) {
        this.project = project;
        this.extensionWriter = extensionWriter;
    }

    public boolean isAssemblyProject() {
        return !getAssemblyExecutions().isEmpty();
    }

    public List<AssemblyExecution> getAssemblyExecutions() {
        if (assemblyExecutions != null) {
            return assemblyExecutions;
        }
        assemblyExecutions = new ArrayList<>();
        if (project == null || project.getBuildPlugins() == null) {
            return assemblyExecutions;
        }
        for (Plugin plugin : project.getBuildPlugins()) {
            if ("maven-assembly-plugin".equals(plugin.getArtifactId())) {
                Xpp3Dom pluginConfig = (Xpp3Dom) plugin.getConfiguration();
                if (plugin.getExecutions() != null && !plugin.getExecutions().isEmpty()) {
                    for (PluginExecution exec : plugin.getExecutions()) {
                        Xpp3Dom execConfig = (Xpp3Dom) exec.getConfiguration();
                        Xpp3Dom config = Xpp3Dom.mergeXpp3Dom(execConfig, pluginConfig);
                        parseAssemblyExecutions(config, assemblyExecutions);
                    }
                } else if (pluginConfig != null) {
                    parseAssemblyExecutions(pluginConfig, assemblyExecutions);
                }
            }
        }
        return assemblyExecutions;
    }

    private void parseAssemblyExecutions(Xpp3Dom config, List<AssemblyExecution> result) {
        if (config == null) {
            return;
        }
        List<String> descriptorPaths = new ArrayList<>();
        Xpp3Dom descriptorsNode = config.getChild("descriptors");
        if (descriptorsNode != null) {
            for (Xpp3Dom dNode : descriptorsNode.getChildren("descriptor")) {
                if (dNode.getValue() != null && !dNode.getValue().trim().isEmpty()) {
                    descriptorPaths.add(dNode.getValue().trim());
                }
            }
        }
        Xpp3Dom singleDescNode = config.getChild("descriptor");
        if (singleDescNode != null
                && singleDescNode.getValue() != null
                && !singleDescNode.getValue().trim().isEmpty()) {
            descriptorPaths.add(singleDescNode.getValue().trim());
        }

        for (String descPath : descriptorPaths) {
            File descFile = new File(project.getBasedir(), descPath);
            if (!descFile.exists()) {
                descFile = new File(descPath);
            }
            if (descFile.exists()) {
                try (Reader reader = Files.newBufferedReader(descFile.toPath(), StandardCharsets.UTF_8)) {
                    Xpp3Dom assemblyDom = Xpp3DomBuilder.build(reader);
                    AssemblyExecution exec = parseAssemblyDom(assemblyDom, config, descFile.getParentFile());
                    result.add(exec);
                } catch (IOException | XmlPullParserException e) {
                    // ponytail: ignore unparseable descriptor, fallback to standard targets
                }
            }
        }
    }

    private AssemblyExecution parseAssemblyDom(Xpp3Dom assemblyDom, Xpp3Dom pluginConfig, File baseDir) {
        AssemblyExecution exec = new AssemblyExecution();
        Xpp3Dom idNode = assemblyDom.getChild("id");
        if (idNode != null) {
            exec.setId(idNode.getValue());
        }
        Xpp3Dom formatsNode = assemblyDom.getChild("formats");
        if (formatsNode != null) {
            for (Xpp3Dom f : formatsNode.getChildren("format")) {
                if (f.getValue() != null) {
                    exec.addFormat(f.getValue());
                }
            }
        }
        Xpp3Dom ibd = assemblyDom.getChild("includeBaseDirectory");
        if (ibd != null && ibd.getValue() != null) {
            exec.setIncludeBaseDirectory(Boolean.parseBoolean(ibd.getValue().trim()));
        }
        Xpp3Dom bd = assemblyDom.getChild("baseDirectory");
        if (bd != null) {
            exec.setBaseDirectory(bd.getValue());
        }
        Xpp3Dom fn = pluginConfig != null ? pluginConfig.getChild("finalName") : null;
        if (fn != null) {
            exec.setFinalName(fn.getValue());
        }
        Xpp3Dom aai = pluginConfig != null ? pluginConfig.getChild("appendAssemblyId") : null;
        if (aai != null && aai.getValue() != null) {
            exec.setAppendAssemblyId(Boolean.parseBoolean(aai.getValue().trim()));
        }
        Xpp3Dom aaiDom = assemblyDom.getChild("appendAssemblyId");
        if (aaiDom != null && aaiDom.getValue() != null) {
            exec.setAppendAssemblyId(Boolean.parseBoolean(aaiDom.getValue().trim()));
        }
        Xpp3Dom tlfm = pluginConfig != null ? pluginConfig.getChild("tarLongFileMode") : null;
        if (tlfm != null && tlfm.getValue() != null) {
            exec.setTarLongFileMode(tlfm.getValue().trim());
        }
        Xpp3Dom tlfmDom = assemblyDom.getChild("tarLongFileMode");
        if (tlfmDom != null && tlfmDom.getValue() != null) {
            exec.setTarLongFileMode(tlfmDom.getValue().trim());
        }

        Xpp3Dom compsNode = assemblyDom.getChild("componentDescriptors");
        if (compsNode != null) {
            for (Xpp3Dom compNode : compsNode.getChildren("componentDescriptor")) {
                if (compNode.getValue() != null) {
                    String compPath = compNode.getValue().trim();
                    File compFile = new File(project.getBasedir(), compPath);
                    if (!compFile.exists() && baseDir != null) {
                        compFile = new File(baseDir, compPath);
                    }
                    if (compFile.exists()) {
                        try (Reader r = Files.newBufferedReader(compFile.toPath(), StandardCharsets.UTF_8)) {
                            Xpp3Dom compDom = Xpp3DomBuilder.build(r);
                            parseAssemblyComponents(compDom, exec);
                        } catch (IOException | XmlPullParserException e) {
                            // ponytail: skip unparseable component descriptor
                        }
                    }
                }
            }
        }

        parseAssemblyComponents(assemblyDom, exec);
        return exec;
    }

    private void parseAssemblyComponents(Xpp3Dom dom, AssemblyExecution exec) {
        if (dom == null) {
            return;
        }
        Xpp3Dom fileSetsNode = dom.getChild("fileSets");
        if (fileSetsNode != null) {
            for (Xpp3Dom fsNode : fileSetsNode.getChildren("fileSet")) {
                AssemblyExecution.AssemblyFileSet fs = new AssemblyExecution.AssemblyFileSet();
                Xpp3Dom dirNode = fsNode.getChild("directory");
                if (dirNode != null) {
                    fs.setDirectory(dirNode.getValue());
                }
                Xpp3Dom outDirNode = fsNode.getChild("outputDirectory");
                if (outDirNode != null) {
                    fs.setOutputDirectory(outDirNode.getValue());
                }
                Xpp3Dom fmNode = fsNode.getChild("fileMode");
                if (fmNode != null) {
                    fs.setFileMode(fmNode.getValue());
                }
                Xpp3Dom leNode = fsNode.getChild("lineEnding");
                if (leNode != null) {
                    fs.setLineEnding(leNode.getValue());
                }
                Xpp3Dom incs = fsNode.getChild("includes");
                if (incs != null) {
                    for (Xpp3Dom inc : incs.getChildren("include")) {
                        if (inc.getValue() != null) {
                            fs.getIncludes().add(inc.getValue().trim());
                        }
                    }
                }
                Xpp3Dom excs = fsNode.getChild("excludes");
                if (excs != null) {
                    for (Xpp3Dom exc : excs.getChildren("exclude")) {
                        if (exc.getValue() != null) {
                            fs.getExcludes().add(exc.getValue().trim());
                        }
                    }
                }
                exec.getFileSets().add(fs);
            }
        }

        Xpp3Dom depSetsNode = dom.getChild("dependencySets");
        if (depSetsNode != null) {
            for (Xpp3Dom dsNode : depSetsNode.getChildren("dependencySet")) {
                AssemblyExecution.AssemblyDependencySet ds = new AssemblyExecution.AssemblyDependencySet();
                Xpp3Dom outDirNode = dsNode.getChild("outputDirectory");
                if (outDirNode != null) {
                    ds.setOutputDirectory(outDirNode.getValue());
                }
                Xpp3Dom upa = dsNode.getChild("useProjectArtifact");
                if (upa != null && upa.getValue() != null) {
                    ds.setUseProjectArtifact(Boolean.parseBoolean(upa.getValue().trim()));
                }
                Xpp3Dom sc = dsNode.getChild("scope");
                if (sc != null && sc.getValue() != null) {
                    ds.setScope(sc.getValue().trim());
                }
                Xpp3Dom incs = dsNode.getChild("includes");
                if (incs != null) {
                    for (Xpp3Dom inc : incs.getChildren("include")) {
                        if (inc.getValue() != null) {
                            ds.getIncludes().add(inc.getValue().trim());
                        }
                    }
                }
                Xpp3Dom excs = dsNode.getChild("excludes");
                if (excs != null) {
                    for (Xpp3Dom exc : excs.getChildren("exclude")) {
                        if (exc.getValue() != null) {
                            ds.getExcludes().add(exc.getValue().trim());
                        }
                    }
                }
                exec.getDependencySets().add(ds);
            }
        }

        Xpp3Dom filesNode = dom.getChild("files");
        if (filesNode != null) {
            for (Xpp3Dom fNode : filesNode.getChildren("file")) {
                AssemblyExecution.AssemblyFile f = new AssemblyExecution.AssemblyFile();
                Xpp3Dom srcNode = fNode.getChild("source");
                if (srcNode != null) {
                    f.setSource(srcNode.getValue());
                }
                Xpp3Dom outDirNode = fNode.getChild("outputDirectory");
                if (outDirNode != null) {
                    f.setOutputDirectory(outDirNode.getValue());
                }
                Xpp3Dom dn = fNode.getChild("destName");
                if (dn != null) {
                    f.setDestName(dn.getValue());
                }
                Xpp3Dom fmNode = fNode.getChild("fileMode");
                if (fmNode != null) {
                    f.setFileMode(fmNode.getValue());
                }
                exec.getFiles().add(f);
            }
        }
    }

    public void writeAssemblyTarget(
            XMLWriter writer, ArtifactResolverWrapper wrapper, List<MavenProject> reactorProjects) {
        XmlWriterUtil.writeCommentText(writer, "Assembly target", 1);
        writer.startElement("target");
        writer.addAttribute("name", "assembly");
        if (extensionWriter != null && extensionWriter.isStandalone()) {
            writer.addAttribute("description", "Create assembly artifacts (no-op in standalone mode)");
            writer.endElement(); // target
            XmlWriterUtil.writeLineBreak(writer);
            return;
        }

        String depends = (extensionWriter != null && extensionWriter.isDependencyUnpackProject())
                ? "compile, unpack-dependencies"
                : "compile";
        writer.addAttribute("depends", depends);
        writer.addAttribute("description", "Create assembly artifacts");

        String workDir = "${maven.build.dir}/assembly/work";
        writer.startElement("mkdir");
        writer.addAttribute("dir", workDir);
        writer.endElement(); // mkdir

        Set<String> createdDirs = new HashSet<>();
        createdDirs.add(workDir);

        for (AssemblyExecution exec : getAssemblyExecutions()) {
            writeDependencySets(writer, exec, workDir, wrapper, reactorProjects, createdDirs);
            writeFileSets(writer, exec, workDir, createdDirs);
            writeFiles(writer, exec, workDir, createdDirs);
            writeArchives(writer, exec, workDir);
        }

        writer.endElement(); // target
        XmlWriterUtil.writeLineBreak(writer);
    }

    private void writeDependencySets(
            XMLWriter writer,
            AssemblyExecution exec,
            String workDir,
            ArtifactResolverWrapper wrapper,
            List<MavenProject> reactorProjects,
            Set<String> createdDirs) {
        List<Artifact> allArtifacts = new ArrayList<>();
        if (project.getArtifacts() != null) {
            allArtifacts.addAll(project.getArtifacts());
        }

        for (AssemblyExecution.AssemblyDependencySet ds : exec.getDependencySets()) {
            String outDir = ds.getOutputDirectory();
            String targetDir = workDir;
            if (outDir != null && !outDir.isEmpty() && !outDir.equals("./") && !outDir.equals(".")) {
                targetDir = workDir + "/" + outDir;
            }
            if (createdDirs.add(targetDir)) {
                writer.startElement("mkdir");
                writer.addAttribute("dir", targetDir);
                writer.endElement(); // mkdir
            }

            for (Artifact artifact : allArtifacts) {
                if (!ds.isUseProjectArtifact()
                        && artifact.getGroupId().equals(project.getGroupId())
                        && artifact.getArtifactId().equals(project.getArtifactId())) {
                    continue;
                }
                if ((Artifact.SCOPE_TEST.equals(artifact.getScope())
                                || Artifact.SCOPE_PROVIDED.equals(artifact.getScope()))
                        && (ds.getScope() == null
                                || (!ds.getScope().equals(artifact.getScope())
                                        && !ds.getScope().equals("test")))) {
                    continue;
                }
                if (!ds.getIncludes().isEmpty() && !matchesAssemblyPattern(artifact, ds.getIncludes())) {
                    continue;
                }
                if (!ds.getExcludes().isEmpty() && matchesAssemblyPattern(artifact, ds.getExcludes())) {
                    continue;
                }

                MavenProject sibling = AntBuildWriterUtil.findReactorProject(artifact, reactorProjects);
                if (sibling != null) {
                    String siblingJar = AntBuildWriterUtil.toRelative(
                                    project.getBasedir(), sibling.getBasedir().getAbsolutePath())
                            + "/target/" + sibling.getBuild().getFinalName() + ".jar";
                    writer.startElement("copy");
                    writer.addAttribute("file", siblingJar);
                    writer.addAttribute("todir", targetDir);
                    writer.addAttribute("failonerror", "false");
                    writer.endElement(); // copy
                } else {
                    String repoPath = wrapper != null ? wrapper.getLocalArtifactPath(artifact) : null;
                    if (repoPath != null) {
                        writer.startElement("copy");
                        writer.addAttribute("file", "${maven.repo.local}/" + repoPath);
                        writer.addAttribute("todir", targetDir);
                        writer.addAttribute("failonerror", "false");
                        writer.endElement(); // copy
                    }
                }
            }
        }
    }

    private void writeFileSets(XMLWriter writer, AssemblyExecution exec, String workDir, Set<String> createdDirs) {
        for (AssemblyExecution.AssemblyFileSet fs : exec.getFileSets()) {
            String dir = fs.getDirectory();
            if (dir == null || dir.isEmpty() || dir.equals(".")) {
                dir = "${basedir}";
            } else {
                dir = toAntOutputDir(dir);
            }

            String outDir = fs.getOutputDirectory();
            String targetDir = workDir;
            if (outDir != null && !outDir.isEmpty() && !outDir.equals("./") && !outDir.equals(".")) {
                targetDir = workDir + "/" + outDir;
            }
            if (createdDirs.add(targetDir)) {
                writer.startElement("mkdir");
                writer.addAttribute("dir", targetDir);
                writer.endElement(); // mkdir
            }

            writer.startElement("copy");
            writer.addAttribute("todir", targetDir);
            writer.addAttribute("failonerror", "false");
            writer.startElement("fileset");
            writer.addAttribute("dir", dir);
            writer.addAttribute("erroronmissingdir", "false");
            for (String inc : fs.getIncludes()) {
                writer.startElement("include");
                writer.addAttribute("name", inc);
                writer.endElement(); // include
            }
            for (String exc : fs.getExcludes()) {
                writer.startElement("exclude");
                writer.addAttribute("name", exc);
                writer.endElement(); // exclude
            }
            writer.endElement(); // fileset
            writer.endElement(); // copy

            if (dir.contains("maven-shared-archive-resources")) {
                writer.startElement("copy");
                writer.addAttribute("todir", targetDir);
                writer.addAttribute("failonerror", "false");
                writer.startElement("fileset");
                writer.addAttribute("dir", "..");
                writer.addAttribute("erroronmissingdir", "false");
                writer.startElement("include");
                writer.addAttribute("name", "LICENSE");
                writer.endElement();
                writer.startElement("include");
                writer.addAttribute("name", "NOTICE");
                writer.endElement();
                writer.endElement(); // fileset
                writer.endElement(); // copy
            }

            if (fs.getFileMode() != null && !fs.getFileMode().trim().isEmpty()) {
                writer.startElement("chmod");
                writer.addAttribute("dir", targetDir);
                writer.addAttribute("perm", fs.getFileMode().trim());
                for (String inc : fs.getIncludes()) {
                    writer.startElement("include");
                    writer.addAttribute("name", inc);
                    writer.endElement(); // include
                }
                writer.endElement(); // chmod
            }

            if (fs.getLineEnding() != null
                    && ("dos".equalsIgnoreCase(fs.getLineEnding()) || "crlf".equalsIgnoreCase(fs.getLineEnding()))) {
                writer.startElement("fixcrlf");
                writer.addAttribute("srcdir", targetDir);
                writer.addAttribute("eol", "crlf");
                for (String inc : fs.getIncludes()) {
                    writer.startElement("include");
                    writer.addAttribute("name", inc);
                    writer.endElement(); // include
                }
                writer.endElement(); // fixcrlf
            }
        }
    }

    private void writeFiles(XMLWriter writer, AssemblyExecution exec, String workDir, Set<String> createdDirs) {
        for (AssemblyExecution.AssemblyFile f : exec.getFiles()) {
            String outDir = f.getOutputDirectory();
            String targetDir = workDir;
            if (outDir != null && !outDir.isEmpty() && !outDir.equals("./") && !outDir.equals(".")) {
                targetDir = workDir + "/" + outDir;
            }
            if (createdDirs.add(targetDir)) {
                writer.startElement("mkdir");
                writer.addAttribute("dir", targetDir);
                writer.endElement(); // mkdir
            }
            String dest = f.getDestName() != null ? targetDir + "/" + f.getDestName() : targetDir;
            writer.startElement("copy");
            writer.addAttribute("file", f.getSource());
            writer.addAttribute("tofile", dest);
            writer.addAttribute("failonerror", "false");
            writer.endElement(); // copy

            if (f.getFileMode() != null && !f.getFileMode().trim().isEmpty()) {
                writer.startElement("chmod");
                writer.addAttribute("file", dest);
                writer.addAttribute("perm", f.getFileMode().trim());
                writer.endElement(); // chmod
            }
        }
    }

    private void writeArchives(XMLWriter writer, AssemblyExecution exec, String workDir) {
        List<String> execPatterns = new ArrayList<>();
        for (AssemblyExecution.AssemblyFileSet fs : exec.getFileSets()) {
            if ("0755".equals(fs.getFileMode()) || "755".equals(fs.getFileMode())) {
                String outDir = fs.getOutputDirectory();
                String prefix = (outDir != null && !outDir.isEmpty() && !outDir.equals("./") && !outDir.equals("."))
                        ? outDir + "/"
                        : "";
                for (String inc : fs.getIncludes()) {
                    execPatterns.add(prefix + inc);
                }
            }
        }
        if (execPatterns.isEmpty()) {
            execPatterns.add("bin/*");
        }

        String finalName = exec.getFinalName() != null ? exec.getFinalName() : "${maven.build.finalName}";
        String id = exec.getId();
        String archiveBase = "${maven.build.dir}/" + finalName;
        if (exec.isAppendAssemblyId() && id != null && !id.isEmpty()) {
            archiveBase = archiveBase + "-" + id;
        }

        String archivePrefix = exec.isIncludeBaseDirectory()
                ? ((exec.getBaseDirectory() != null && !exec.getBaseDirectory().isEmpty())
                        ? exec.getBaseDirectory()
                        : finalName)
                : null;

        for (String format : exec.getFormats()) {
            if ("zip".equalsIgnoreCase(format)) {
                writeZipArchive(writer, archiveBase + ".zip", workDir, archivePrefix, execPatterns);
            } else if ("tar.gz".equalsIgnoreCase(format) || "tgz".equalsIgnoreCase(format)) {
                writeTarArchive(
                        writer,
                        archiveBase + ".tar.gz",
                        workDir,
                        archivePrefix,
                        execPatterns,
                        "gzip",
                        exec.getTarLongFileMode());
            } else if ("tar.bz2".equalsIgnoreCase(format) || "tbz2".equalsIgnoreCase(format)) {
                writeTarArchive(
                        writer,
                        archiveBase + ".tar.bz2",
                        workDir,
                        archivePrefix,
                        execPatterns,
                        "bzip2",
                        exec.getTarLongFileMode());
            } else if ("tar".equalsIgnoreCase(format)) {
                writeTarArchive(
                        writer,
                        archiveBase + ".tar",
                        workDir,
                        archivePrefix,
                        execPatterns,
                        null,
                        exec.getTarLongFileMode());
            }
        }
    }

    private void writeZipArchive(
            XMLWriter writer, String destFile, String workDir, String archivePrefix, List<String> execPatterns) {
        writer.startElement("zip");
        writer.addAttribute("destfile", destFile);
        if (!execPatterns.isEmpty()) {
            writer.startElement("zipfileset");
            writer.addAttribute("dir", workDir);
            if (archivePrefix != null) {
                writer.addAttribute("prefix", archivePrefix);
            }
            writer.addAttribute("filemode", "755");
            for (String pat : execPatterns) {
                writer.startElement("include");
                writer.addAttribute("name", pat);
                writer.endElement(); // include
            }
            writer.endElement(); // zipfileset

            writer.startElement("zipfileset");
            writer.addAttribute("dir", workDir);
            if (archivePrefix != null) {
                writer.addAttribute("prefix", archivePrefix);
            }
            for (String pat : execPatterns) {
                writer.startElement("exclude");
                writer.addAttribute("name", pat);
                writer.endElement(); // exclude
            }
            writer.endElement(); // zipfileset
        } else {
            writer.startElement("zipfileset");
            writer.addAttribute("dir", workDir);
            if (archivePrefix != null) {
                writer.addAttribute("prefix", archivePrefix);
            }
            writer.endElement(); // zipfileset
        }
        writer.endElement(); // zip
    }

    private void writeTarArchive(
            XMLWriter writer,
            String destFile,
            String workDir,
            String archivePrefix,
            List<String> execPatterns,
            String compression,
            String longFileMode) {
        writer.startElement("tar");
        writer.addAttribute("destfile", destFile);
        if (compression != null) {
            writer.addAttribute("compression", compression);
        }
        writer.addAttribute("longfile", longFileMode);
        if (!execPatterns.isEmpty()) {
            writer.startElement("tarfileset");
            writer.addAttribute("dir", workDir);
            if (archivePrefix != null) {
                writer.addAttribute("prefix", archivePrefix);
            }
            writer.addAttribute("filemode", "755");
            for (String pat : execPatterns) {
                writer.startElement("include");
                writer.addAttribute("name", pat);
                writer.endElement(); // include
            }
            writer.endElement(); // tarfileset

            writer.startElement("tarfileset");
            writer.addAttribute("dir", workDir);
            if (archivePrefix != null) {
                writer.addAttribute("prefix", archivePrefix);
            }
            for (String pat : execPatterns) {
                writer.startElement("exclude");
                writer.addAttribute("name", pat);
                writer.endElement(); // exclude
            }
            writer.endElement(); // tarfileset
        } else {
            writer.startElement("tarfileset");
            writer.addAttribute("dir", workDir);
            if (archivePrefix != null) {
                writer.addAttribute("prefix", archivePrefix);
            }
            writer.endElement(); // tarfileset
        }
        writer.endElement(); // tar
    }

    private boolean matchesAssemblyPattern(Artifact artifact, List<String> patterns) {
        String id1 = artifact.getGroupId() + ":" + artifact.getArtifactId();
        String id2 = artifact.getArtifactId();
        for (String pattern : patterns) {
            if ("*".equals(pattern) || "*.*".equals(pattern) || "*:*".equals(pattern)) {
                return true;
            }
            if (pattern.equals(id1) || pattern.equals(id2)) {
                return true;
            }
            if (pattern.endsWith(":*")) {
                String prefix = pattern.substring(0, pattern.length() - 2);
                if (prefix.equals(artifact.getGroupId())) {
                    return true;
                }
            }
            if (pattern.startsWith("*:") || pattern.startsWith(":")) {
                String part = pattern.startsWith("*:") ? pattern.substring(2) : pattern.substring(1);
                if (part.equals(artifact.getArtifactId()) || part.startsWith(artifact.getArtifactId() + ":")) {
                    return true;
                }
            }
            if (pattern.startsWith(id1 + ":")) {
                return true;
            }
        }
        return false;
    }

    private String toAntOutputDir(String dir) {
        if (dir == null) {
            return "${maven.build.dir}";
        }
        String s = dir.replace('\\', '/');
        if (s.startsWith("${project.build.directory}")) {
            return "${maven.build.dir}" + s.substring("${project.build.directory}".length());
        }
        if (s.startsWith("${basedir}/")) {
            return s.substring("${basedir}/".length());
        }
        if (s.startsWith("target/")) {
            return "${maven.build.dir}/" + s.substring("target/".length());
        }
        return s;
    }
}
