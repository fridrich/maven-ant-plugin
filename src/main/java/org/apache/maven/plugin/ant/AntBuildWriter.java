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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.tools.ant.Main;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.XMLWriter;
import org.codehaus.plexus.util.xml.XmlWriterUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Write Ant build files from <code>Maven Project</code> for <a href="http://ant.apache.org">Ant</a> 1.6.2 or above:
 * <ul>
 * <li>build.xml</li>
 * <li>maven-build.xml</li>
 * <li>maven-build.properties</li>
 * </ul>
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 * @version $Id: AntBuildWriter.java 1645084 2014-12-12 22:28:31Z khmarbaise $
 */
public class AntBuildWriter {
    /**
     * The default line indenter
     */
    protected static final int DEFAULT_INDENTATION_SIZE = XmlWriterUtil.DEFAULT_INDENTATION_SIZE;

    /**
     * The default build file name (build.xml)
     */
    protected static final String DEFAULT_BUILD_FILENAME = Main.DEFAULT_BUILD_FILENAME;

    /**
     * The default generated build file name
     */
    protected static final String DEFAULT_MAVEN_BUILD_FILENAME = "maven-build.xml";

    /**
     * The default build properties file name
     */
    protected static final String DEFAULT_MAVEN_PROPERTIES_FILENAME = "maven-build.properties";

    private final MavenProject project;

    private final ArtifactResolverWrapper artifactResolverWrapper;

    private final File localRepository;

    private final Settings settings;

    private final boolean overwrite;

    private final Properties executionProperties;

    private final AntExtensionWriter extensionWriter;

    private final AntTestWriter testWriter;

    private final Set<Artifact> injectedArtifacts = new LinkedHashSet<Artifact>();

    private final List<MavenProject> reactorProjects;

    /**
     * @param project {@link MavenProject}
     * @param artifactResolverWrapper {@link ArtifactResolverWrapper}
     * @param settings {@link Settings}
     * @param overwrite true/false to overwrite or not.
     * @param executionProperties {@link Properties}
     * @param reactorProjects {@link List}
     */
    public AntBuildWriter(
            MavenProject project,
            ArtifactResolverWrapper artifactResolverWrapper,
            Settings settings,
            boolean overwrite,
            Properties executionProperties,
            List<MavenProject> reactorProjects) {
        this.project = project;
        this.artifactResolverWrapper = artifactResolverWrapper;
        this.localRepository = artifactResolverWrapper.getLocalRepositoryDirectory();
        this.settings = settings;
        this.overwrite = overwrite;
        this.executionProperties = (executionProperties != null) ? executionProperties : new Properties();
        this.extensionWriter = new AntExtensionWriter(project);
        this.testWriter = new AntTestWriter(project);
        this.reactorProjects = reactorProjects;
    }

    private MavenProject findReactorProject(Artifact artifact) {
        if (reactorProjects != null) {
            for (MavenProject reactorProj : reactorProjects) {
                if (reactorProj.getGroupId().equals(artifact.getGroupId())
                        && reactorProj.getArtifactId().equals(artifact.getArtifactId())) {
                    return reactorProj;
                }
            }
        }
        return null;
    }

    /**
     * Generate Ant build XML files
     *
     * @throws IOException In case of an error.
     */
    protected void writeBuildXmls() throws IOException {
        writeGeneratedBuildXml();
        writeBuildXml();
    }

    /**
     * Generate <code>maven-build.properties</code> only for a non-POM project
     *
     * @throws IOException In case of an failure {@link IOException}
     * @see #DEFAULT_MAVEN_PROPERTIES_FILENAME
     */
    protected void writeBuildProperties() throws IOException {
        if (AntBuildWriterUtil.isPomPackaging(project)) {
            return;
        }

        Properties properties = new Properties();

        // ----------------------------------------------------------------------
        // Build properties
        // ----------------------------------------------------------------------

        addProperty(
                properties,
                "maven.build.finalName",
                AntBuildWriterUtil.toRelative(
                        project.getBasedir(), project.getBuild().getFinalName()));

        addProperty(properties, "project.groupId", project.getGroupId());
        addProperty(properties, "project.artifactId", project.getArtifactId());
        addProperty(properties, "project.version", project.getVersion());

        String specVersion = AntBuildWriterUtil.getSpecificationVersion(project.getVersion());
        if (specVersion != null) {
            addProperty(properties, "spec.version", specVersion);
        }

        if (project.getName() != null) {
            addProperty(properties, "project.name", project.getName());
        }
        if (project.getDescription() != null) {
            addProperty(properties, "project.description", project.getDescription());
        }
        if (project.getOrganization() != null && project.getOrganization().getName() != null) {
            addProperty(
                    properties,
                    "project.organization.name",
                    project.getOrganization().getName());
        }
        if (project.getUrl() != null) {
            addProperty(properties, "project.url", project.getUrl());
        }

        // target
        addProperty(
                properties,
                "maven.build.dir",
                AntBuildWriterUtil.toRelative(
                        project.getBasedir(), project.getBuild().getDirectory()));
        addProperty(properties, "project.build.directory", "${maven.build.dir}");

        // ${maven.build.dir}/classes
        addProperty(
                properties,
                "maven.build.outputDir",
                "${maven.build.dir}/"
                        + AntBuildWriterUtil.toRelative(
                                new File(project.getBasedir(), properties.getProperty("maven.build.dir")),
                                project.getBuild().getOutputDirectory()));
        addProperty(properties, "project.build.outputDirectory", "${maven.build.outputDir}");

        // src/main/java
        if (!project.getCompileSourceRoots().isEmpty()) {
            List<String> compileSourceRoots = project.getCompileSourceRoots();
            for (int i = 0; i < compileSourceRoots.size(); i++) {
                addProperty(
                        properties,
                        "maven.build.srcDir." + i,
                        AntBuildWriterUtil.toRelative(project.getBasedir(), compileSourceRoots.get(i)));
            }
        }
        // src/main/resources
        if (project.getBuild().getResources() != null) {
            List<Resource> resources = project.getBuild().getResources();
            for (int i = 0; i < resources.size(); i++) {
                addProperty(
                        properties,
                        "maven.build.resourceDir." + i,
                        AntBuildWriterUtil.toRelative(
                                project.getBasedir(), resources.get(i).getDirectory()));
            }
        }

        // ${maven.build.dir}/test-classes
        addProperty(
                properties,
                "maven.build.testOutputDir",
                "${maven.build.dir}/"
                        + AntBuildWriterUtil.toRelative(
                                new File(project.getBasedir(), properties.getProperty("maven.build.dir")),
                                project.getBuild().getTestOutputDirectory()));
        // src/test/java
        if (!project.getTestCompileSourceRoots().isEmpty()) {
            List<String> compileSourceRoots = project.getTestCompileSourceRoots();
            for (int i = 0; i < compileSourceRoots.size(); i++) {
                addProperty(
                        properties,
                        "maven.build.testDir." + i,
                        AntBuildWriterUtil.toRelative(project.getBasedir(), compileSourceRoots.get(i)));
            }
        }
        // src/test/resources
        if (project.getBuild().getTestResources() != null) {
            List<Resource> resources = project.getBuild().getTestResources();
            for (int i = 0; i < resources.size(); i++) {
                addProperty(
                        properties,
                        "maven.build.testResourceDir." + i,
                        AntBuildWriterUtil.toRelative(
                                project.getBasedir(), resources.get(i).getDirectory()));
            }
        }

        addProperty(properties, "maven.test.reports", "${maven.build.dir}/test-reports");

        addProperty(properties, "maven.reporting.outputDirectory", "${maven.build.dir}/site");

        // ----------------------------------------------------------------------
        // Settings properties
        // ----------------------------------------------------------------------

        addProperty(properties, "maven.settings.offline", String.valueOf(settings.isOffline()));
        addProperty(properties, "maven.settings.interactiveMode", String.valueOf(settings.isInteractiveMode()));
        addProperty(properties, "maven.repo.local", getLocalRepositoryPath());

        // ----------------------------------------------------------------------
        // Project properties
        // ----------------------------------------------------------------------

        if (project.getProperties() != null) {
            for (Map.Entry<Object, Object> objectObjectEntry :
                    project.getProperties().entrySet()) {
                Map.Entry property = (Map.Entry) objectObjectEntry;
                addProperty(
                        properties,
                        property.getKey().toString(),
                        property.getValue().toString());
            }
        }

        try (FileOutputStream os =
                new FileOutputStream(new File(project.getBasedir(), DEFAULT_MAVEN_PROPERTIES_FILENAME))) {
            properties.store(os, "Generated by Maven Ant Plugin - DO NOT EDIT THIS FILE!");
        }
    }

    /**
     * Generate an <code>maven-build.xml</code>
     *
     * @throws IOException
     * @see #DEFAULT_MAVEN_BUILD_FILENAME
     */
    private void writeGeneratedBuildXml() throws IOException {
        // TODO: parameter
        File outputFile = new File(project.getBasedir(), DEFAULT_MAVEN_BUILD_FILENAME);

        String encoding = "UTF-8";

        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(outputFile), encoding)) {
            XMLWriter writer =
                    new PrettyPrintXMLWriter(w, StringUtils.repeat(" ", DEFAULT_INDENTATION_SIZE), encoding, null);

            // ----------------------------------------------------------------------
            // <!-- comments -->
            // ----------------------------------------------------------------------

            AntBuildWriterUtil.writeHeader(writer);

            // ----------------------------------------------------------------------
            // <project/>
            // ----------------------------------------------------------------------

            writer.startElement("project");
            writer.addAttribute("name", project.getArtifactId() + "-from-maven");
            writer.addAttribute("default", "package");
            writer.addAttribute("basedir", ".");

            XmlWriterUtil.writeLineBreak(writer);

            // ----------------------------------------------------------------------
            // <property/>
            // ----------------------------------------------------------------------

            writeProperties(writer);

            // ----------------------------------------------------------------------
            // <path/>
            // ----------------------------------------------------------------------

            writeBuildPathDefinition(writer);

            // ----------------------------------------------------------------------
            // <target name="clean" />
            // ----------------------------------------------------------------------

            writeCleanTarget(writer);

            // ----------------------------------------------------------------------
            // <target name="templates|javacc|jflex|cup" />
            // ----------------------------------------------------------------------
            if (!AntBuildWriterUtil.isPomPackaging(project)
                    && (extensionWriter.isJavaccProject()
                            || extensionWriter.isTemplatingProject()
                            || extensionWriter.isJflexProject()
                            || extensionWriter.isCupProject())) {
                extensionWriter.writeGenSourcesTarget(writer);
            }

            // ----------------------------------------------------------------------
            // <target name="compile" />
            // ----------------------------------------------------------------------

            List<String> compileSourceRoots =
                    AntBuildWriterUtil.removeEmptyCompileSourceRoots(project.getCompileSourceRoots());
            writeCompileTarget(writer, compileSourceRoots);

            // ----------------------------------------------------------------------
            // <target name="compile-tests" />
            // ----------------------------------------------------------------------

            List<String> testCompileSourceRoots =
                    AntBuildWriterUtil.removeEmptyCompileSourceRoots(project.getTestCompileSourceRoots());
            writeCompileTestsTarget(writer, testCompileSourceRoots);

            // ----------------------------------------------------------------------
            // <target name="test" />
            // ----------------------------------------------------------------------

            writeTestTargets(writer, testCompileSourceRoots);

            // ----------------------------------------------------------------------
            // <target name="javadoc" />
            // ----------------------------------------------------------------------
            writeJavadocTarget(writer, compileSourceRoots);

            // ----------------------------------------------------------------------
            // <target name="sisu" />
            // ----------------------------------------------------------------------
            if (!AntBuildWriterUtil.isPomPackaging(project) && extensionWriter.isSisuProject()) {
                extensionWriter.writeSisuTarget(writer);
            }

            // ----------------------------------------------------------------------
            // <target name="package" />
            // ----------------------------------------------------------------------
            writePackageTarget(writer);

            // ----------------------------------------------------------------------
            // <target name="get-deps" />
            // ----------------------------------------------------------------------
            writeGetDepsTarget(writer);

            XmlWriterUtil.writeLineBreak(writer);

            writer.endElement(); // project

            XmlWriterUtil.writeLineBreak(writer);
        }
    }

    /**
     * Generate an generic <code>build.xml</code> if not already exist
     *
     * @throws IOException
     * @see #DEFAULT_BUILD_FILENAME
     */
    private void writeBuildXml() throws IOException {
        File outputFile = new File(project.getBasedir(), DEFAULT_BUILD_FILENAME);

        if (outputFile.exists() && !overwrite) {
            return;
        }

        String encoding = "UTF-8";

        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(outputFile), encoding)) {
            XMLWriter writer =
                    new PrettyPrintXMLWriter(w, StringUtils.repeat(" ", DEFAULT_INDENTATION_SIZE), encoding, null);

            // ----------------------------------------------------------------------
            // <!-- comments -->
            // ----------------------------------------------------------------------

            AntBuildWriterUtil.writeAntVersionHeader(writer);

            // ----------------------------------------------------------------------
            // <project/>
            // ----------------------------------------------------------------------

            writer.startElement("project");
            writer.addAttribute("name", project.getArtifactId());
            writer.addAttribute("default", "package");
            writer.addAttribute("basedir", ".");

            XmlWriterUtil.writeLineBreak(writer);

            XmlWriterUtil.writeCommentText(
                    writer, "Import " + DEFAULT_MAVEN_BUILD_FILENAME + " into the current project", 1);

            writer.startElement("import");
            writer.addAttribute("file", DEFAULT_MAVEN_BUILD_FILENAME);
            writer.endElement(); // import

            XmlWriterUtil.writeLineBreak(writer, 1, 1);

            XmlWriterUtil.writeCommentText(writer, "Help target", 1);

            writer.startElement("target");
            writer.addAttribute("name", "help");

            writer.startElement("echo");
            writer.addAttribute("message", "Please run: $ant -projecthelp");
            writer.endElement(); // echo

            writer.endElement(); // target

            XmlWriterUtil.writeLineBreak(writer, 2);

            writer.endElement(); // project

            XmlWriterUtil.writeLineBreak(writer);
        }
    }

    /**
     * Write properties in the writer only for a non-POM project.
     *
     * @param writer
     */
    @SuppressWarnings("checkstyle:MethodLength")
    private void writeProperties(XMLWriter writer) {
        if (AntBuildWriterUtil.isPomPackaging(project)) {
            return;
        }

        // TODO: optional in m1
        // TODO: USD properties
        XmlWriterUtil.writeCommentText(writer, "Build environment properties", 1);

        // ----------------------------------------------------------------------
        // File properties to override local properties
        // ----------------------------------------------------------------------

        writer.startElement("property");
        writer.addAttribute("file", "${user.home}/.m2/maven.properties");
        writer.endElement(); // property

        writer.startElement("property");
        writer.addAttribute("file", DEFAULT_MAVEN_PROPERTIES_FILENAME);
        writer.endElement(); // property

        // ----------------------------------------------------------------------
        // Build properties
        // ----------------------------------------------------------------------

        XmlWriterUtil.writeLineBreak(writer, 2, 1);

        writer.startElement("property");
        writer.addAttribute("name", "maven.build.finalName");
        writer.addAttribute("value", project.getBuild().getFinalName());
        writer.endElement(); // property

        writer.startElement("property");
        writer.addAttribute("name", "project.groupId");
        writer.addAttribute("value", project.getGroupId());
        writer.endElement(); // property

        writer.startElement("property");
        writer.addAttribute("name", "project.artifactId");
        writer.addAttribute("value", project.getArtifactId());
        writer.endElement(); // property

        writer.startElement("property");
        writer.addAttribute("name", "project.version");
        writer.addAttribute("value", project.getVersion());
        writer.endElement(); // property

        String specVersion = AntBuildWriterUtil.getSpecificationVersion(project.getVersion());
        if (specVersion != null) {
            writer.startElement("property");
            writer.addAttribute("name", "spec.version");
            writer.addAttribute("value", specVersion);
            writer.endElement(); // property
        }

        if (project.getName() != null) {
            writer.startElement("property");
            writer.addAttribute("name", "project.name");
            writer.addAttribute("value", project.getName());
            writer.endElement(); // property
        }

        if (project.getDescription() != null) {
            writer.startElement("property");
            writer.addAttribute("name", "project.description");
            writer.addAttribute("value", project.getDescription());
            writer.endElement(); // property
        }

        if (project.getOrganization() != null && project.getOrganization().getName() != null) {
            writer.startElement("property");
            writer.addAttribute("name", "project.organization.name");
            writer.addAttribute("value", project.getOrganization().getName());
            writer.endElement(); // property
        }

        if (project.getUrl() != null) {
            writer.startElement("property");
            writer.addAttribute("name", "project.url");
            writer.addAttribute("value", project.getUrl());
            writer.endElement(); // property
        }

        writer.startElement("property");
        writer.addAttribute("name", "maven.build.dir");
        writer.addAttribute(
                "value",
                AntBuildWriterUtil.toRelative(
                        project.getBasedir(), project.getBuild().getDirectory()));
        writer.endElement(); // property

        writer.startElement("property");
        writer.addAttribute("name", "project.build.directory");
        writer.addAttribute("value", "${maven.build.dir}");
        writer.endElement(); // property

        writer.startElement("property");
        writer.addAttribute("name", "maven.build.outputDir");
        writer.addAttribute(
                "value",
                "${maven.build.dir}/"
                        + AntBuildWriterUtil.toRelative(
                                new File(project.getBuild().getDirectory()),
                                project.getBuild().getOutputDirectory()));
        writer.endElement(); // property

        writer.startElement("property");
        writer.addAttribute("name", "project.build.outputDirectory");
        writer.addAttribute("value", "${maven.build.outputDir}");
        writer.endElement(); // property

        if (!project.getCompileSourceRoots().isEmpty()) {
            List<String> compileSourceRoots = project.getCompileSourceRoots();
            for (int i = 0; i < compileSourceRoots.size(); i++) {
                writer.startElement("property");
                writer.addAttribute("name", "maven.build.srcDir." + i);
                writer.addAttribute(
                        "value", AntBuildWriterUtil.toRelative(project.getBasedir(), compileSourceRoots.get(i)));
                writer.endElement(); // property
            }
        }

        if (project.getBuild().getResources() != null) {
            List<Resource> resources = project.getBuild().getResources();
            for (int i = 0; i < resources.size(); i++) {
                writer.startElement("property");
                writer.addAttribute("name", "maven.build.resourceDir." + i);
                writer.addAttribute(
                        "value",
                        AntBuildWriterUtil.toRelative(
                                project.getBasedir(), resources.get(i).getDirectory()));
                writer.endElement(); // property
            }
        }

        writer.startElement("property");
        writer.addAttribute("name", "maven.build.testOutputDir");
        writer.addAttribute(
                "value",
                "${maven.build.dir}/"
                        + AntBuildWriterUtil.toRelative(
                                new File(project.getBuild().getDirectory()),
                                project.getBuild().getTestOutputDirectory()));
        writer.endElement(); // property

        if (!project.getTestCompileSourceRoots().isEmpty()) {
            List<String> compileSourceRoots = project.getTestCompileSourceRoots();
            for (int i = 0; i < compileSourceRoots.size(); i++) {
                writer.startElement("property");
                writer.addAttribute("name", "maven.build.testDir." + i);
                writer.addAttribute(
                        "value", AntBuildWriterUtil.toRelative(project.getBasedir(), compileSourceRoots.get(i)));
                writer.endElement(); // property
            }
        }

        if (project.getBuild().getTestResources() != null) {
            List<Resource> resources = project.getBuild().getTestResources();
            for (int i = 0; i < resources.size(); i++) {
                writer.startElement("property");
                writer.addAttribute("name", "maven.build.testResourceDir." + i);
                writer.addAttribute(
                        "value",
                        AntBuildWriterUtil.toRelative(
                                project.getBasedir(), resources.get(i).getDirectory()));
                writer.endElement(); // property
            }
        }

        writer.startElement("property");
        writer.addAttribute("name", "maven.test.reports");
        writer.addAttribute("value", "${maven.build.dir}/test-reports");
        writer.endElement(); // property

        String reportingOutputDir = (project.getModel().getReporting() != null)
                ? project.getModel().getReporting().getOutputDirectory()
                : new File(project.getBuild().getDirectory(), "site").getAbsolutePath();
        // workaround for MNG-3475
        if (!new File(reportingOutputDir).isAbsolute()) {
            reportingOutputDir = new File(project.getBasedir(), reportingOutputDir).getAbsolutePath();
        }
        writer.startElement("property");
        writer.addAttribute("name", "maven.reporting.outputDirectory");
        writer.addAttribute(
                "value",
                "${maven.build.dir}/"
                        + AntBuildWriterUtil.toRelative(
                                new File(project.getBuild().getDirectory()), reportingOutputDir));
        writer.endElement(); // property

        // ----------------------------------------------------------------------
        // Setting properties
        // ----------------------------------------------------------------------

        XmlWriterUtil.writeLineBreak(writer, 2, 1);

        writer.startElement("property");
        writer.addAttribute("name", "maven.repo.local");
        writer.addAttribute("value", getLocalRepositoryPath());
        writer.endElement(); // property

        writer.startElement("property");
        writer.addAttribute("name", "maven.settings.offline");
        writer.addAttribute("value", String.valueOf(settings.isOffline()));
        writer.endElement(); // property

        writer.startElement("property");
        writer.addAttribute("name", "maven.settings.interactiveMode");
        writer.addAttribute("value", String.valueOf(settings.isInteractiveMode()));
        writer.endElement(); // property

        // ----------------------------------------------------------------------
        // Project properties
        // ----------------------------------------------------------------------

        if (project.getProperties() != null && !project.getProperties().isEmpty()) {
            XmlWriterUtil.writeLineBreak(writer, 2, 1);
            XmlWriterUtil.writeCommentText(writer, "Project properties", 1);
            List<String> propertyNames = new ArrayList<>(project.getProperties().stringPropertyNames());
            Collections.sort(propertyNames);
            for (String name : propertyNames) {
                String value = project.getProperties().getProperty(name);
                if (value != null) {
                    writer.startElement("property");
                    writer.addAttribute("name", name);
                    writer.addAttribute("value", value);
                    writer.endElement(); // property
                }
            }
        }

        List<CompilerExecution> compilerExecutions = AntBuildWriterUtil.getCompilerExecutions(project);
        Set<Integer> jreVersions = new TreeSet<Integer>();
        int baseVersion = AntBuildWriterUtil.getBaseCompileVersion(project, compilerExecutions);
        for (CompilerExecution exec : compilerExecutions) {
            if (AntBuildWriterUtil.isMultiReleaseExecution(exec, baseVersion, project)) {
                String ver = exec.getRelease() != null ? exec.getRelease() : exec.getTarget();
                try {
                    int intVer = (int) Double.parseDouble(ver);
                    if (intVer > 8) {
                        jreVersions.add(intVer);
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }

        if (!jreVersions.isEmpty()) {
            XmlWriterUtil.writeLineBreak(writer, 2, 1);
            XmlWriterUtil.writeCommentText(writer, "Multi-Release JDK Support", 1);
            for (int ver : jreVersions) {
                writer.startElement("condition");
                writer.addAttribute("property", "jdk" + ver + ".supported");
                writer.startElement("javaversion");
                writer.addAttribute("atleast", String.valueOf(ver));
                writer.endElement(); // javaversion
                writer.endElement(); // condition
            }
        }

        XmlWriterUtil.writeLineBreak(writer);
    }

    /**
     * Check if the local repository is in the default location: <code>${user.home}/.m2/repository</code>. If that is
     * the case then return
     * the path with the system property "user.home" in it. If not then just
     * return the absolute path to the local repository.
     */
    private String getLocalRepositoryPath() {
        if (localRepository == null) {
            return "${user.home}/.m2/repository";
        }
        String userHome = System.getProperty("user.home");
        String defaultPath = (userHome + "/.m2/repository").replace('\\', '/');
        String actualPath = localRepository.getAbsolutePath().replace('\\', '/');
        if (actualPath.equals(defaultPath)) {
            return "${user.home}/.m2/repository";
        } else {
            return localRepository.getAbsolutePath();
        }
    }

    /**
     * Write path definition in the writer only for a non-POM project.
     *
     * @param writer
     */
    @SuppressWarnings("deprecation")
    private void writeBuildPathDefinition(XMLWriter writer) throws IOException {
        if (AntBuildWriterUtil.isPomPackaging(project)) {
            return;
        }

        XmlWriterUtil.writeCommentText(writer, "Defining classpaths", 1);

        writeBuildPathDefinition(writer, "build.classpath", project.getCompileArtifacts());

        writeBuildPathDefinition(writer, "build.test.classpath", project.getArtifacts());

        XmlWriterUtil.writeLineBreak(writer);
    }

    private void writeBuildPathDefinition(XMLWriter writer, String id, Collection<Artifact> artifacts)
            throws IOException {
        writer.startElement("path");
        writer.addAttribute("id", id);

        boolean sisuInjectPresent = false;
        boolean bndAntPresent = false;
        boolean javaccPresent = false;
        for (Artifact artifact : artifacts) {
            if ("org.eclipse.sisu.inject".equals(artifact.getArtifactId())) {
                sisuInjectPresent = true;
            }
            if ("biz.aQute.bnd.ant".equals(artifact.getArtifactId())) {
                bndAntPresent = true;
            }
            if ("javacc".equals(artifact.getArtifactId())) {
                javaccPresent = true;
            }

            writer.startElement("pathelement");

            String path;
            MavenProject sibling = findReactorProject(artifact);
            if (sibling != null) {
                String relativePath = AntBuildWriterUtil.toRelative(
                        project.getBasedir(), sibling.getBasedir().getAbsolutePath());
                path = relativePath + "/target/classes";
            } else if (Artifact.SCOPE_SYSTEM.equals(artifact.getScope())) {
                path = getUninterpolatedSystemPath(artifact);
            } else {
                path = "${maven.repo.local}/" + artifactResolverWrapper.getLocalArtifactPath(artifact);
            }
            writer.addAttribute("location", path);

            writer.endElement(); // pathelement
        }

        if (extensionWriter.isSisuProject() && !sisuInjectPresent && "build.classpath".equals(id)) {
            try {
                String sisuVer = extensionWriter.getSisuVersion();
                Set<Artifact> resolved = artifactResolverWrapper.resolveTransitively(
                        "org.eclipse.sisu", "org.eclipse.sisu.inject", sisuVer);
                injectedArtifacts.addAll(resolved);
                for (Artifact art : resolved) {
                    writer.startElement("pathelement");
                    writer.addAttribute(
                            "location", "${maven.repo.local}/" + artifactResolverWrapper.getLocalArtifactPath(art));
                    writer.endElement(); // pathelement
                }
            } catch (Exception e) {
                // ignore
            }
        }

        if (extensionWriter.isBndProject() && !bndAntPresent && "build.classpath".equals(id)) {
            resolveBndClasspaths(writer);
        }

        if (extensionWriter.isJavaccProject() && !javaccPresent && "build.classpath".equals(id)) {
            try {
                // Always classic JavaCC, even for ph-javacc-maven-plugin projects (which use the
                // parser-generator-cc fork instead) - functionally fine, minor output differences.
                Set<Artifact> resolved =
                        artifactResolverWrapper.resolveTransitively("net.java.dev.javacc", "javacc", "7.0.12");
                injectedArtifacts.addAll(resolved);
                for (Artifact art : resolved) {
                    writer.startElement("pathelement");
                    writer.addAttribute(
                            "location", "${maven.repo.local}/" + artifactResolverWrapper.getLocalArtifactPath(art));
                    writer.endElement(); // pathelement
                }
            } catch (Exception e) {
                // ignore
            }
        }

        if (extensionWriter.isJflexProject() && "build.classpath".equals(id)) {
            try {
                // jflex-generated lexers always implement java_cup.runtime.Scanner, even without
                // CUP; transitive resolution misses it (version property in jflex's parent pom).
                Set<Artifact> resolved = new HashSet<>();
                resolved.addAll(artifactResolverWrapper.resolveTransitively(
                        "de.jflex", "jflex", extensionWriter.getJflexVersion()));
                resolved.addAll(artifactResolverWrapper.resolveTransitively(
                        "com.github.vbmacher", "java-cup-runtime", "11b-20160615-1"));
                injectedArtifacts.addAll(resolved);
                for (Artifact art : resolved) {
                    writer.startElement("pathelement");
                    writer.addAttribute(
                            "location", "${maven.repo.local}/" + artifactResolverWrapper.getLocalArtifactPath(art));
                    writer.endElement(); // pathelement
                }
            } catch (Exception e) {
                // ignore
            }
        }

        if (extensionWriter.isCupProject() && "build.classpath".equals(id)) {
            try {
                String cupVer = extensionWriter.getCupVersion();
                Set<Artifact> resolved =
                        artifactResolverWrapper.resolveTransitively("com.github.vbmacher", "java-cup", cupVer);
                injectedArtifacts.addAll(resolved);
                for (Artifact art : resolved) {
                    writer.startElement("pathelement");
                    writer.addAttribute(
                            "location", "${maven.repo.local}/" + artifactResolverWrapper.getLocalArtifactPath(art));
                    writer.endElement(); // pathelement
                }
            } catch (Exception e) {
                // ignore
            }
        }

        writer.endElement(); // path
    }

    private void resolveBndClasspaths(XMLWriter writer) throws IOException {
        try {
            Set<Artifact> resolved = new HashSet<>();
            resolved.addAll(artifactResolverWrapper.resolveTransitively("biz.aQute.bnd", "biz.aQute.bnd.ant", "7.4.0"));
            resolved.addAll(artifactResolverWrapper.resolveTransitively("biz.aQute.bnd", "biz.aQute.bndlib", "7.4.0"));
            resolved.addAll(
                    artifactResolverWrapper.resolveTransitively("biz.aQute.bnd", "biz.aQute.bnd.util", "7.4.0"));
            resolved.addAll(artifactResolverWrapper.resolveTransitively("org.osgi", "org.osgi.core", "6.0.0"));
            resolved.addAll(artifactResolverWrapper.resolveTransitively("org.slf4j", "slf4j-api", "1.7.36"));
            resolved.addAll(artifactResolverWrapper.resolveTransitively("org.slf4j", "slf4j-simple", "1.7.36"));
            resolved.addAll(
                    artifactResolverWrapper.resolveTransitively("org.osgi", "org.osgi.service.repository", "1.1.0"));
            resolved.addAll(artifactResolverWrapper.resolveTransitively("org.osgi", "org.osgi.service.log", "1.4.0"));
            resolved.addAll(artifactResolverWrapper.resolveTransitively("org.osgi", "org.osgi.util.promise", "1.2.0"));
            resolved.addAll(artifactResolverWrapper.resolveTransitively("org.osgi", "org.osgi.util.function", "1.2.0"));
            injectedArtifacts.addAll(resolved);

            for (Artifact art : resolved) {
                writer.startElement("pathelement");
                writer.addAttribute(
                        "location", "${maven.repo.local}/" + artifactResolverWrapper.getLocalArtifactPath(art));
                writer.endElement(); // pathelement
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private String getUninterpolatedSystemPath(Artifact artifact) {
        String managementKey = artifact.getDependencyConflictId();

        for (Dependency dependency : project.getOriginalModel().getDependencies()) {
            if (managementKey.equals(dependency.getManagementKey())) {
                return dependency.getSystemPath();
            }
        }

        for (Profile profile : project.getOriginalModel().getProfiles()) {
            for (Dependency dependency : profile.getDependencies()) {
                if (managementKey.equals(dependency.getManagementKey())) {
                    return dependency.getSystemPath();
                }
            }
        }

        String path = artifact.getFile().getAbsolutePath();

        Properties props = new Properties();
        props.putAll(project.getProperties());
        props.putAll(executionProperties);
        props.remove("user.dir");
        props.put("basedir", project.getBasedir().getAbsolutePath());

        SortedMap<String, String> candidateProperties = new TreeMap<String, String>();
        for (Object o : props.keySet()) {
            String key = (String) o;
            String value = new File(props.getProperty(key)).getPath();
            if (path.startsWith(value) && value.length() > 0) {
                candidateProperties.put(value, key);
            }
        }
        if (!candidateProperties.isEmpty()) {
            String value = candidateProperties.lastKey();
            String key = candidateProperties.get(value);
            path = path.substring(value.length());
            path = path.replace('\\', '/');
            return "${" + key + "}" + path;
        }

        return path;
    }

    /**
     * Write clean target in the writer depending the packaging of the project.
     *
     * @param writer the writer
     */
    private void writeCleanTarget(XMLWriter writer) {
        XmlWriterUtil.writeCommentText(writer, "Cleaning up target", 1);

        writer.startElement("target");
        writer.addAttribute("name", "clean");
        writer.addAttribute("description", "Clean the output directory");

        if (AntBuildWriterUtil.isPomPackaging(project)) {
            if (project.getModules() != null) {
                for (String moduleSubPath : project.getModules()) {
                    AntBuildWriterUtil.writeAntTask(writer, project, moduleSubPath, "clean");
                }
            }
        } else {
            writer.startElement("delete");
            writer.addAttribute("dir", "${maven.build.dir}");
            writer.endElement(); // delete
        }

        writer.endElement(); // target

        XmlWriterUtil.writeLineBreak(writer);
    }

    /**
     * Write compile target in the writer depending the packaging of the project.
     *
     * @param writer
     * @param compileSourceRoots
     * @throws IOException if any
     */
    private void writeCompileTarget(XMLWriter writer, List<String> compileSourceRoots) throws IOException {
        XmlWriterUtil.writeCommentText(writer, "Compilation target", 1);

        if (AntBuildWriterUtil.isPomPackaging(project)) {
            writer.startElement("target");
            writer.addAttribute("name", "compile");
            writer.addAttribute("description", "Compile the code");
            if (project.getModules() != null) {
                for (String moduleSubPath : project.getModules()) {
                    AntBuildWriterUtil.writeAntTask(writer, project, moduleSubPath, "compile");
                }
            }
            writer.endElement(); // target
        } else {
            List<CompilerExecution> compilerExecutions = AntBuildWriterUtil.getCompilerExecutions(project);
            Set<Integer> mrVersions = new TreeSet<>();
            int baseVersion = AntBuildWriterUtil.getBaseCompileVersion(project, compilerExecutions);
            for (CompilerExecution exec : compilerExecutions) {
                if (AntBuildWriterUtil.isMultiReleaseExecution(exec, baseVersion, project)) {
                    String ver = exec.getRelease() != null ? exec.getRelease() : exec.getTarget();
                    try {
                        int intVer = (int) Double.parseDouble(ver);
                        if (intVer > 8 && !exec.getCompileSourceRoots().isEmpty()) {
                            mrVersions.add(intVer);
                        }
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }

            String genSourceTargets = extensionWriter.getGenSourceTargets();
            String baseDepends = genSourceTargets.isEmpty() ? "get-deps" : genSourceTargets;

            if (mrVersions.isEmpty()) {
                writer.startElement("target");
                writer.addAttribute("name", "compile");
                writer.addAttribute("depends", baseDepends);
                writer.addAttribute("description", "Compile the code");

                writeCompileTasks(
                        writer,
                        "${maven.build.outputDir}",
                        compileSourceRoots,
                        project.getBuild().getResources(),
                        null,
                        false);

                writer.endElement(); // target
            } else {
                writer.startElement("target");
                writer.addAttribute("name", "compile-base");
                writer.addAttribute("depends", baseDepends);
                writer.addAttribute("description", "Compile the base code");

                writeCompileTasks(
                        writer,
                        "${maven.build.outputDir}",
                        compileSourceRoots,
                        project.getBuild().getResources(),
                        null,
                        false);

                writer.endElement(); // target
                XmlWriterUtil.writeLineBreak(writer);

                String prevTarget = "compile-base";
                StringBuilder dependsList = new StringBuilder("compile-base");
                for (int ver : mrVersions) {
                    writer.startElement("target");
                    writer.addAttribute("name", "compile-java" + ver);
                    writer.addAttribute("depends", prevTarget);
                    writer.addAttribute("if", "jdk" + ver + ".supported");
                    writer.addAttribute("description", "Compile the Java " + ver + " code");

                    extensionWriter.writeCompileMRTasks(writer, "${maven.build.outputDir}", ver, compilerExecutions);

                    writer.endElement(); // target
                    XmlWriterUtil.writeLineBreak(writer);

                    dependsList.append(",compile-java").append(ver);
                    prevTarget = "compile-java" + ver;
                }

                writer.startElement("target");
                writer.addAttribute("name", "compile");
                writer.addAttribute("depends", dependsList.toString());
                writer.addAttribute("description", "Compile the code");
                writer.endElement(); // target
            }
        }

        XmlWriterUtil.writeLineBreak(writer);
    }

    /**
     * Write compile-test target in the writer depending the packaging of the project.
     *
     * @param writer
     * @param testCompileSourceRoots
     * @throws IOException if any
     */
    private void writeCompileTestsTarget(XMLWriter writer, List<String> testCompileSourceRoots) throws IOException {
        XmlWriterUtil.writeCommentText(writer, "Test-compilation target", 1);

        if (AntBuildWriterUtil.isPomPackaging(project)) {
            writer.startElement("target");
            writer.addAttribute("name", "compile-tests");
            writer.addAttribute("description", "Compile the test code");
            if (project.getModules() != null) {
                for (String moduleSubPath : project.getModules()) {
                    AntBuildWriterUtil.writeAntTask(writer, project, moduleSubPath, "compile-tests");
                }
            }
            writer.endElement(); // target
        } else {
            writer.startElement("target");
            writer.addAttribute("name", "compile-tests");
            AntBuildWriterUtil.addWrapAttribute(writer, "target", "depends", "compile", 2);
            AntBuildWriterUtil.addWrapAttribute(writer, "target", "description", "Compile the test code", 2);
            AntBuildWriterUtil.addWrapAttribute(writer, "target", "unless", "maven.test.skip", 2);

            writeCompileTasks(
                    writer,
                    "${maven.build.testOutputDir}",
                    testCompileSourceRoots,
                    project.getBuild().getTestResources(),
                    "${maven.build.outputDir}",
                    true);

            writer.endElement(); // target
        }

        XmlWriterUtil.writeLineBreak(writer);
    }

    /**
     * Write test target in the writer depending the packaging of the project.
     *
     * @param writer
     * @param testCompileSourceRoots
     */
    private void writeTestTargets(XMLWriter writer, List<String> testCompileSourceRoots) throws IOException {
        XmlWriterUtil.writeCommentText(writer, "Run all tests", 1);

        if (AntBuildWriterUtil.isPomPackaging(project)) {
            writePomParts(writer);
        } else {
            testWriter.writeTestTargets(writer, testCompileSourceRoots, getTestIncludes(), getTestExcludes());
        }

        XmlWriterUtil.writeLineBreak(writer);
    }

    /**
     * @param writer {@link XMLWriter}
     */
    private void writePomParts(XMLWriter writer) {
        writer.startElement("target");
        writer.addAttribute("name", "test");
        writer.addAttribute("description", "Run the test cases");
        if (project.getModules() != null) {
            for (String moduleSubPath : project.getModules()) {
                AntBuildWriterUtil.writeAntTask(writer, project, moduleSubPath, "test");
            }
        }
        writer.endElement(); // target
    }

    /**
     * Gets the include patterns for the unit tests.
     *
     * @return A list of strings with include patterns, might be empty but never <code>null</code>.
     */
    private List<String> getTestIncludes() throws IOException {
        // CHECKSTYLE_OFF: LineLength
        List<String> includes =
                getSelectorList(AntBuildWriterUtil.getMavenSurefirePluginOptions(project, "includes", null));
        // CHECKSTYLE_ON: LineLength
        if (includes == null || includes.isEmpty()) {
            includes = Arrays.asList("**/Test*.java", "**/*Test.java", "**/*TestCase.java");
        }
        return includes;
    }

    /**
     * Gets the exclude patterns for the unit tests.
     *
     * @return A list of strings with exclude patterns, might be empty but never <code>null</code>.
     */
    private List<String> getTestExcludes() throws IOException {
        // CHECKSTYLE_OFF: LineLength
        List<String> excludes =
                getSelectorList(AntBuildWriterUtil.getMavenSurefirePluginOptions(project, "excludes", null));
        // CHECKSTYLE_ON: LineLength
        if (excludes == null || excludes.isEmpty()) {
            excludes = Collections.singletonList("**/*Abstract*Test.java");
        }
        return excludes;
    }

    /**
     * Write javadoc target in the writer depending the packaging of the project.
     *
     * @param writer
     * @param compileSourceRoots
     * @throws IOException if any
     */
    private void writeJavadocTarget(XMLWriter writer, List<String> compileSourceRoots) throws IOException {
        XmlWriterUtil.writeCommentText(writer, "Javadoc target", 1);

        writer.startElement("target");
        writer.addAttribute("name", "javadoc");

        if (!AntBuildWriterUtil.isPomPackaging(project)) {
            String genSourceTargets = extensionWriter.getGenSourceTargets();
            if (!genSourceTargets.isEmpty()) {
                writer.addAttribute("depends", genSourceTargets);
            }
        }
        writer.addAttribute("description", "Generates the Javadoc of the application");

        if (AntBuildWriterUtil.isPomPackaging(project)) {
            if (project.getModules() != null) {
                for (String moduleSubPath : project.getModules()) {
                    AntBuildWriterUtil.writeAntTask(writer, project, moduleSubPath, "javadoc");
                }
            }
        } else {
            List<String> extraSourceDirs = getExtraGeneratedSourceDirs(compileSourceRoots);
            extraSourceDirs.addAll(getExtraStaticSourceDirs(compileSourceRoots));
            AntBuildWriterUtil.writeJavadocTask(writer, project, artifactResolverWrapper, extraSourceDirs);
        }

        writer.endElement(); // target

        XmlWriterUtil.writeLineBreak(writer);
    }

    /**
     * Write package target in the writer depending the packaging of the project.
     *
     * @param writer
     * @throws IOException if any
     */
    private void writePackageTarget(XMLWriter writer) throws IOException {
        String synonym = null; // type of the package we are creating (for example jar)
        XmlWriterUtil.writeCommentText(writer, "Package target", 1);

        writer.startElement("target");
        writer.addAttribute("name", "package");

        if (!AntBuildWriterUtil.isPomPackaging(project)) {
            String dependsList;
            if (extensionWriter.isBndProject()) {
                dependsList = "compile,test";
            } else if (extensionWriter.isSisuProject()) {
                dependsList = "sisu,test";
            } else {
                dependsList = "compile,test";
            }
            writer.addAttribute("depends", dependsList);
        }
        writer.addAttribute("description", "Package the application");

        if (AntBuildWriterUtil.isPomPackaging(project)) {
            if (project.getModules() != null) {
                for (String moduleSubPath : project.getModules()) {
                    AntBuildWriterUtil.writeAntTask(writer, project, moduleSubPath, "package");
                }
            }
        } else {
            if (AntBuildWriterUtil.isJarPackaging(project)) {
                AntBuildWriterUtil.writeJarTask(writer, project);

                if (extensionWriter.isBndProject()) {
                    // wraps the jar just built above, in place
                    extensionWriter.writeBndWrapSequence(writer);
                }

                synonym = "jar";
            } else if (AntBuildWriterUtil.isEarPackaging(project)) {
                AntBuildWriterUtil.writeEarTask(writer, project, artifactResolverWrapper);
                synonym = "ear";
            } else if (AntBuildWriterUtil.isWarPackaging(project)) {
                AntBuildWriterUtil.writeWarTask(writer, project, artifactResolverWrapper);
                synonym = "war";
            } else {
                writer.startElement("echo");
                writer.addAttribute(
                        "message",
                        "No Ant task exists for the packaging '" + project.getPackaging() + "'. "
                                + "You could overrided the Ant package target in your build.xml.");
                writer.endElement(); // echo
            }
        }

        writer.endElement(); // target

        XmlWriterUtil.writeLineBreak(writer);

        if (synonym != null) {
            // CHECKSTYLE_OFF: LineLength
            XmlWriterUtil.writeCommentText(writer, "A dummy target for the package named after the type it creates", 1);
            // CHECKSTYLE_ON: LineLength
            writer.startElement("target");
            writer.addAttribute("name", synonym);
            writer.addAttribute("depends", "package");
            writer.addAttribute("description", "Builds the " + synonym + " for the application");
            writer.endElement(); // target

            XmlWriterUtil.writeLineBreak(writer);
        }
    }

    /**
     * JavaCC/JJTree and templating output dirs not already a compile source root; need their own
     * mkdir + pathelement. Written unconditionally rather than relying on project.getCompileSourceRoots()
     * containing them, since that requires generate-sources to have actually run for this project
     * (not the case for a bare `mvn ant:ant`, only when chained after `generate-sources`).
     */
    private List<String> getExtraGeneratedSourceDirs(List<String> compileSourceRoots) {
        List<String> dirs = new ArrayList<>();

        if (extensionWriter.isJavaccProject()) {
            for (JavaccExecution exec : extensionWriter.getJavaccExecutions()) {
                Xpp3Dom config = exec.getConfiguration();
                String goal = exec.getGoal();
                String javaccOutputDir = config.getChild("outputDirectory") != null
                        ? config.getChild("outputDirectory").getValue()
                        : "${project.build.directory}/generated-sources/"
                                + (goal.contains("jjtree") ? "jjtree" : "javacc");

                if (javaccOutputDir.contains("${project.build.directory}")) {
                    javaccOutputDir = javaccOutputDir.replace("${project.build.directory}", "${maven.build.dir}");
                }

                if (!isCompileSourceRoot(compileSourceRoots, javaccOutputDir) && !dirs.contains(javaccOutputDir)) {
                    dirs.add(javaccOutputDir);
                }
            }
        }

        if (extensionWriter.isTemplatingProject()) {
            String templatingOutputDir = "${maven.build.dir}/generated-sources/java-templates";
            if (!isCompileSourceRoot(compileSourceRoots, templatingOutputDir) && !dirs.contains(templatingOutputDir)) {
                dirs.add(templatingOutputDir);
            }
        }

        if (extensionWriter.isJflexProject()) {
            for (AntExtensionWriter.JflexExecution exec : extensionWriter.getJflexExecutions()) {
                addParserOutputDir(
                        dirs,
                        compileSourceRoots,
                        exec.getConfiguration(),
                        "${maven.build.dir}/generated-sources/jflex");
            }
        }

        if (extensionWriter.isCupProject()) {
            for (AntExtensionWriter.CupExecution exec : extensionWriter.getCupExecutions()) {
                addParserOutputDir(
                        dirs, compileSourceRoots, exec.getConfiguration(), "${maven.build.dir}/generated-sources/cup");
            }
        }

        return dirs;
    }

    private void addParserOutputDir(
            List<String> dirs, List<String> compileSourceRoots, Xpp3Dom config, String defaultDir) {
        String outputDir = defaultDir;
        if (config != null && config.getChild("outputDirectory") != null) {
            outputDir = config.getChild("outputDirectory").getValue();
        }
        if (outputDir.contains("${project.build.directory}")) {
            outputDir = outputDir.replace("${project.build.directory}", "${maven.build.dir}");
        }
        if (!isCompileSourceRoot(compileSourceRoots, outputDir) && !dirs.contains(outputDir)) {
            dirs.add(outputDir);
        }
    }

    private boolean isCompileSourceRoot(List<String> compileSourceRoots, String dir) {
        for (String root : compileSourceRoots) {
            String relRoot = AntBuildWriterUtil.toRelative(project.getBasedir(), root);
            String relDir = dir.startsWith("${maven.build.dir}") ? dir.replace("${maven.build.dir}", "target") : dir;
            if (root.contains(relDir) || relRoot.contains(relDir) || relDir.contains(relRoot)) {
                return true;
            }
        }
        return false;
    }

    /**
     * build-helper-maven-plugin add-source dirs not already a compile source root. Unlike
     * getExtraGeneratedSourceDirs, these are hand-written committed sources, not build output, so
     * they get a pathelement but no mkdir - a missing one is a real error, not something to paper over.
     */
    private List<String> getExtraStaticSourceDirs(List<String> compileSourceRoots) {
        List<String> dirs = new ArrayList<>();
        for (String addSourceDir : extensionWriter.getAddSourceDirs()) {
            File dirFile = new File(addSourceDir);
            String absoluteDir =
                    dirFile.isAbsolute() ? addSourceDir : new File(project.getBasedir(), addSourceDir).getPath();
            boolean alreadyPresent = false;
            for (String root : compileSourceRoots) {
                if (new File(root).getAbsolutePath().equals(new File(absoluteDir).getAbsolutePath())) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                String relative = AntBuildWriterUtil.toRelative(project.getBasedir(), absoluteDir);
                if (!dirs.contains(relative)) {
                    dirs.add(relative);
                }
            }
        }
        return dirs;
    }

    @SuppressWarnings("checkstyle:MethodLength")
    private void writeCompileTasks(
            XMLWriter writer,
            String outputDirectory,
            List<String> compileSourceRoots,
            List<Resource> resources,
            String additionalClassesDirectory,
            boolean isTest)
            throws IOException {
        writer.startElement("mkdir");
        writer.addAttribute("dir", outputDirectory);
        writer.endElement(); // mkdir

        if (!compileSourceRoots.isEmpty()) {
            for (int i = 0; i < compileSourceRoots.size(); i++) {
                String srcDir = compileSourceRoots.get(i);
                if (srcDir.contains("generated-sources")
                        || srcDir.contains("generated-test-sources")
                        || srcDir.contains(project.getBuild().getDirectory())) {
                    writer.startElement("mkdir");
                    if (isTest) {
                        writer.addAttribute("dir", "${maven.build.testDir." + i + "}");
                    } else {
                        writer.addAttribute("dir", "${maven.build.srcDir." + i + "}");
                    }
                    writer.endElement(); // mkdir
                }
            }
        }

        List<String> extraGeneratedDirs =
                isTest ? Collections.emptyList() : getExtraGeneratedSourceDirs(compileSourceRoots);
        for (String dir : extraGeneratedDirs) {
            // javac's <src> needs this dir to exist even if the generator target left it empty
            writer.startElement("mkdir");
            writer.addAttribute("dir", dir);
            writer.endElement(); // mkdir
        }

        List<String> extraStaticDirs = isTest ? Collections.emptyList() : getExtraStaticSourceDirs(compileSourceRoots);

        // CHECKSTYLE_OFF: LineLength
        if (!compileSourceRoots.isEmpty()) {
            writer.startElement("javac");
            writer.addAttribute("destdir", outputDirectory);
            AntBuildWriterUtil.addWrapAttribute(
                    writer,
                    "javac",
                    "includeantruntime",
                    AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "includeantruntime", "false"),
                    3);
            Map[] includes = AntBuildWriterUtil.getMavenCompilerPluginOptions(project, "includes", null);
            AntBuildWriterUtil.addWrapAttribute(
                    writer, "javac", "includes", getCommaSeparatedList(includes, "include"), 3);
            Map[] excludes = AntBuildWriterUtil.getMavenCompilerPluginOptions(project, "excludes", null);
            AntBuildWriterUtil.addWrapAttribute(
                    writer, "javac", "excludes", getCommaSeparatedList(excludes, "exclude"), 3);
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
                    AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "showDeprecation", "true"),
                    3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer,
                    "javac",
                    "target",
                    AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "target", "1.8"),
                    3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer,
                    "javac",
                    "verbose",
                    AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "verbose", "false"),
                    3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer,
                    "javac",
                    "fork",
                    AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "fork", "false"),
                    3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer,
                    "javac",
                    "memoryMaximumSize",
                    AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "meminitial", null),
                    3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer,
                    "javac",
                    "memoryInitialSize",
                    AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "maxmem", null),
                    3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer,
                    "javac",
                    "source",
                    AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "source", "1.8"),
                    3);
            AntBuildWriterUtil.addWrapAttribute(
                    writer,
                    "javac",
                    "release",
                    AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, "release", "8"),
                    3);

            if (!compileSourceRoots.isEmpty()) {
                writer.startElement("src");
                for (int i = 0; i < compileSourceRoots.size(); i++) {
                    writer.startElement("pathelement");
                    if (isTest) {
                        writer.addAttribute("location", "${maven.build.testDir." + i + "}");
                    } else {
                        writer.addAttribute("location", "${maven.build.srcDir." + i + "}");
                    }
                    writer.endElement(); // pathelement
                }

                for (String extraDir : extraGeneratedDirs) {
                    writer.startElement("pathelement");
                    writer.addAttribute("location", extraDir);
                    writer.endElement(); // pathelement
                }

                for (String extraDir : extraStaticDirs) {
                    writer.startElement("pathelement");
                    writer.addAttribute("location", extraDir);
                    writer.endElement(); // pathelement
                }

                writer.endElement(); // src
            }

            if (additionalClassesDirectory == null) {
                writer.startElement("classpath");
                if (isTest) {
                    writer.addAttribute("refid", "build.test.classpath");
                } else {
                    writer.addAttribute("refid", "build.classpath");
                }
                writer.endElement(); // classpath
            } else {
                writer.startElement("classpath");
                writer.startElement("path");
                if (isTest) {
                    writer.addAttribute("refid", "build.test.classpath");
                } else {
                    writer.addAttribute("refid", "build.classpath");
                }
                writer.endElement(); // path
                writer.startElement("pathelement");
                writer.addAttribute("location", additionalClassesDirectory);
                writer.endElement(); // pathelement
                writer.endElement(); // classpath
            }

            writer.endElement(); // javac
        }
        // CHECKSTYLE_ON: LineLength

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);

            if (new File(resource.getDirectory()).exists()) {
                String outputDir = outputDirectory;
                if (resource.getTargetPath() != null
                        && !resource.getTargetPath().isEmpty()) {
                    outputDir = outputDir + "/" + resource.getTargetPath();

                    writer.startElement("mkdir");
                    writer.addAttribute("dir", outputDir);
                    writer.endElement(); // mkdir
                }

                writer.startElement("copy");
                writer.addAttribute("todir", outputDir);

                writer.startElement("fileset");
                if (isTest) {
                    writer.addAttribute("dir", "${maven.build.testResourceDir." + i + "}");
                } else {
                    writer.addAttribute("dir", "${maven.build.resourceDir." + i + "}");
                }

                AntBuildWriterUtil.writeIncludesExcludes(writer, resource.getIncludes(), resource.getExcludes());

                writer.endElement(); // fileset

                writer.endElement(); // copy
            }
        }
    }

    /**
     * Write get-deps target in the writer only for a non-POM project
     *
     * @param writer
     */
    private void writeGetDepsTarget(XMLWriter writer) {
        if (AntBuildWriterUtil.isPomPackaging(project)) {
            return;
        }

        XmlWriterUtil.writeCommentText(writer, "Download dependencies target", 1);

        writer.startElement("target");
        writer.addAttribute("name", "test-offline");

        writer.startElement("condition");
        writer.addAttribute("property", "maven.mode.offline");
        writer.startElement("equals");
        writer.addAttribute("arg1", "${maven.settings.offline}");
        writer.addAttribute("arg2", "true");
        writer.endElement(); // equals
        writer.endElement(); // condition
        writer.endElement(); // target

        XmlWriterUtil.writeLineBreak(writer, 2, 1);

        writer.startElement("target");
        writer.addAttribute("name", "get-deps");
        AntBuildWriterUtil.addWrapAttribute(writer, "target", "depends", "test-offline", 2);
        AntBuildWriterUtil.addWrapAttribute(writer, "target", "description", "Download all dependencies", 2);
        AntBuildWriterUtil.addWrapAttribute(writer, "target", "unless", "maven.mode.offline", 2); // TODO: check, and
        // differs from m1

        writer.startElement("mkdir");
        writer.addAttribute("dir", "${maven.repo.local}");
        writer.endElement(); // mkdir

        String basedir = project.getBasedir().getAbsolutePath();

        List<Artifact> allArtifacts = new ArrayList<Artifact>();
        if (project.getArtifacts() != null) {
            allArtifacts.addAll(project.getArtifacts());
        }
        allArtifacts.addAll(injectedArtifacts);

        // CHECKSTYLE_OFF: LineLength
        // TODO: proxy - probably better to use wagon!
        for (Artifact artifact : allArtifacts) {
            if (Artifact.SCOPE_SYSTEM.equals(artifact.getScope())) {
                continue;
            }

            if (findReactorProject(artifact) != null) {
                continue;
            }

            String path = artifactResolverWrapper.getLocalArtifactPath(artifact);

            if (!new File(path).exists()) {
                File parentDirs = new File(path).getParentFile();
                if (parentDirs != null) {
                    writer.startElement("mkdir");
                    // Replace \ with / in the parent dir path
                    writer.addAttribute(
                            "dir", "${maven.repo.local}/" + parentDirs.getPath().replace('\\', '/'));
                    writer.endElement(); // mkdir
                }

                for (Object o1 : project.getRepositories()) {
                    Repository repository = (Repository) o1;
                    String url = repository.getUrl();

                    String localDir = getProjectRepoDirectory(url, basedir);
                    if (localDir != null) {
                        if (localDir.length() > 0 && !localDir.endsWith("/")) {
                            localDir += '/';
                        }

                        writer.startElement("copy");
                        writer.addAttribute("file", localDir + path);
                        AntBuildWriterUtil.addWrapAttribute(writer, "copy", "tofile", "${maven.repo.local}/" + path, 3);
                        AntBuildWriterUtil.addWrapAttribute(writer, "copy", "failonerror", "false", 3);
                        writer.endElement(); // copy
                    } else {
                        writer.startElement("get");
                        writer.addAttribute("src", url + '/' + path);
                        AntBuildWriterUtil.addWrapAttribute(writer, "get", "dest", "${maven.repo.local}/" + path, 3);
                        AntBuildWriterUtil.addWrapAttribute(writer, "get", "usetimestamp", "false", 3);
                        AntBuildWriterUtil.addWrapAttribute(writer, "get", "ignoreerrors", "true", 3);
                        AntBuildWriterUtil.addWrapAttribute(writer, "get", "skipexisting", "true", 3);
                        writer.endElement(); // get
                    }
                }
            }
        }
        // CHECKSTYLE_ON: LineLength

        writer.endElement(); // target

        XmlWriterUtil.writeLineBreak(writer);
    }

    /**
     * Gets the relative path to a repository that is rooted in the project. The returned path (if any) will always use
     * the forward slash ('/') as the directory separator. For example, the path "target/it-repo" will be returned for a
     * repository constructed from the URL "file://${basedir}/target/it-repo".
     *
     * @param repoUrl The URL to the repository, must not be <code>null</code>.
     * @param projectDir The absolute path to the base directory of the project, must not be <code>null</code>
     * @return The path to the repository (relative to the project base directory) or <code>null</code> if the
     *         repository is not rooted in the project.
     */
    static String getProjectRepoDirectory(String repoUrl, String projectDir) {
        try {
            /*
             * NOTE: The usual way of constructing repo URLs rooted in the project is "file://${basedir}" or
             * "file:/${basedir}". None of these forms delivers a valid URL on both Unix and Windows (even ignoring URL
             * encoding), one platform will end up with the first directory of the path being interpreted as the host
             * name...
             */
            if (repoUrl.regionMatches(true, 0, "file://", 0, 7)) {
                String temp = repoUrl.substring(7);
                if (!temp.startsWith("/") && !temp.regionMatches(true, 0, "localhost/", 0, 10)) {
                    repoUrl = "file:///" + temp;
                }
            }
            String path = FileUtils.toFile(new URL(repoUrl)).getPath();
            if (path.startsWith(projectDir)) {
                path = path.substring(projectDir.length()).replace('\\', '/');
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }
                return path;
            }
        } catch (Exception e) {
            // not a "file:" URL or simply malformed
        }
        return null;
    }

    // ----------------------------------------------------------------------
    // Convenience methods
    // ----------------------------------------------------------------------

    /**
     * Put a property in properties defined by a name and a value
     *
     * @param properties not null
     * @param name
     * @param value not null
     */
    private static void addProperty(Properties properties, String name, String value) {
        properties.put(name, StringUtils.isNotEmpty(value) ? value : "");
    }

    /**
     * @param includes an array of includes or exludes map
     * @param key a key wanted in the map, like <code>include</code> or <code>exclude</code>
     * @return a String with comma-separated value of a key in each map
     */
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

    /**
     * Flattens the specified file selector options into a simple string list. For instance, the input
     * <p/>
     *
     * <pre>
     * [ {include=&quot;*Test.java&quot;}, {include=&quot;*TestCase.java&quot;} ]
     * </pre>
     * <p/>
     * is converted to
     * <p/>
     *
     * <pre>
     * [ &quot;*Test.java&quot;, &quot;*TestCase.java&quot; ]
     * </pre>
     *
     * @param options The file selector options to flatten, may be <code>null</code>.
     * @return The string list, might be empty but never <code>null</code>.
     */
    private static List<String> getSelectorList(Map[] options) {
        List<String> list = new ArrayList<>();
        if (options != null && options.length > 0) {
            for (Map option : options) {
                for (Object value : option.values()) {
                    list.add(String.valueOf(value));
                }
            }
        }
        return list;
    }
}
