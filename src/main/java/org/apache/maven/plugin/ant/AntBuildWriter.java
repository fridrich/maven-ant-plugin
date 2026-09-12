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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

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
        this.testWriter = new AntTestWriter(project, extensionWriter);
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
    @SuppressWarnings("checkstyle:MethodLength")
    protected void writeBuildProperties() throws IOException {
        if (AntBuildWriterUtil.isPomPackaging(project)) {
            return;
        }

        Properties properties = new Properties();

        // ----------------------------------------------------------------------
        // Project identification
        // ----------------------------------------------------------------------

        addProperty(properties, "project.groupId", project.getGroupId());
        addProperty(properties, "project.artifactId", project.getArtifactId());
        addProperty(properties, "project.version", project.getVersion());
        addProperty(properties, "spec.version", AntBuildWriterUtil.getSpecificationVersion(project.getVersion()));

        // ----------------------------------------------------------------------
        // Build properties
        // ----------------------------------------------------------------------

        addProperty(
                properties,
                "maven.build.finalName",
                AntBuildWriterUtil.toRelative(
                        project.getBasedir(), project.getBuild().getFinalName()));

        // target
        addProperty(
                properties,
                "maven.build.dir",
                AntBuildWriterUtil.toRelative(
                        project.getBasedir(), project.getBuild().getDirectory()));

        // ${maven.build.dir}/classes
        addProperty(
                properties,
                "maven.build.outputDir",
                "${maven.build.dir}/"
                        + AntBuildWriterUtil.toRelative(
                                new File(project.getBasedir(), properties.getProperty("maven.build.dir")),
                                project.getBuild().getOutputDirectory()));

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

        if (extensionWriter.isModelloProject()) {
            addProperty(properties, "maven.build.mdoDir", extensionWriter.getModelloMdoDir());
            addProperty(properties, "maven.build.mdoOutputDir", "${maven.build.dir}/generated-sources/modello");
        }

        // ----------------------------------------------------------------------
        // Settings properties
        // ----------------------------------------------------------------------

        addProperty(properties, "maven.repo.local", getLocalRepositoryPath());
        addProperty(properties, "maven.settings.offline", String.valueOf(settings.isOffline()));
        addProperty(properties, "maven.settings.interactiveMode", String.valueOf(settings.isInteractiveMode()));

        // ----------------------------------------------------------------------
        // Project metadata and aliases
        // ----------------------------------------------------------------------

        addProperty(properties, "project.name", project.getName());
        addProperty(properties, "project.description", project.getDescription());
        addProperty(properties, "project.url", project.getUrl());
        addProperty(
                properties,
                "project.organization.name",
                project.getOrganization() != null ? project.getOrganization().getName() : null);
        addProperty(
                properties,
                "project.organization.url",
                project.getOrganization() != null ? project.getOrganization().getUrl() : null);
        addProperty(properties, "project.inceptionYear", project.getInceptionYear());
        addProperty(properties, "project.packaging", project.getPackaging());
        addProperty(properties, "project.basedir", "${basedir}");

        addProperty(properties, "project.build.finalName", "${maven.build.finalName}");
        addProperty(properties, "project.build.directory", "${maven.build.dir}");
        addProperty(properties, "project.build.outputDirectory", "${maven.build.outputDir}");
        addProperty(properties, "project.build.testOutputDirectory", "${maven.build.testOutputDir}");

        if (project.getBuild().getSourceDirectory() != null) {
            addProperty(
                    properties,
                    "project.build.sourceDirectory",
                    AntBuildWriterUtil.toRelative(
                            project.getBasedir(), project.getBuild().getSourceDirectory()));
        }
        if (project.getBuild().getTestSourceDirectory() != null) {
            addProperty(
                    properties,
                    "project.build.testSourceDirectory",
                    AntBuildWriterUtil.toRelative(
                            project.getBasedir(), project.getBuild().getTestSourceDirectory()));
        }

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

        // ----------------------------------------------------------------------
        // Legacy POM aliases
        // ----------------------------------------------------------------------

        addProperty(properties, "pom.groupId", "${project.groupId}");
        addProperty(properties, "pom.artifactId", "${project.artifactId}");
        addProperty(properties, "pom.version", "${project.version}");
        addProperty(properties, "pom.name", project.getName() != null ? "${project.name}" : null);
        addProperty(properties, "pom.description", project.getDescription() != null ? "${project.description}" : null);
        addProperty(properties, "pom.url", project.getUrl() != null ? "${project.url}" : null);
        addProperty(
                properties,
                "pom.organization.name",
                project.getOrganization() != null && project.getOrganization().getName() != null
                        ? "${project.organization.name}"
                        : null);
        addProperty(
                properties,
                "pom.organization.url",
                project.getOrganization() != null && project.getOrganization().getUrl() != null
                        ? "${project.organization.url}"
                        : null);
        addProperty(
                properties,
                "pom.inceptionYear",
                project.getInceptionYear() != null ? "${project.inceptionYear}" : null);
        addProperty(properties, "pom.packaging", project.getPackaging() != null ? "${project.packaging}" : null);
        addProperty(properties, "pom.basedir", "${project.basedir}");

        addProperty(properties, "pom.build.finalName", "${project.build.finalName}");
        addProperty(properties, "pom.build.directory", "${project.build.directory}");
        addProperty(properties, "pom.build.outputDirectory", "${project.build.outputDirectory}");
        addProperty(properties, "pom.build.testOutputDirectory", "${project.build.testOutputDirectory}");

        if (project.getBuild().getSourceDirectory() != null) {
            addProperty(properties, "pom.build.sourceDirectory", "${project.build.sourceDirectory}");
        }
        if (project.getBuild().getTestSourceDirectory() != null) {
            addProperty(properties, "pom.build.testSourceDirectory", "${project.build.testSourceDirectory}");
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
            // <target name="templates|javacc|jflex|cup|mdo" />
            // ----------------------------------------------------------------------
            if (!AntBuildWriterUtil.isPomPackaging(project)
                    && (extensionWriter.isJavaccProject()
                            || extensionWriter.isTemplatingProject()
                            || extensionWriter.isJflexProject()
                            || extensionWriter.isCupProject()
                            || extensionWriter.isModelloProject()
                            || extensionWriter.isDependencyUnpackProject())) {
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
                if (!testCompileSourceRoots.isEmpty()) {
                    extensionWriter.writeSisuTestTarget(writer);
                }
            }

            // ----------------------------------------------------------------------
            // <target name="plexus" />
            // ----------------------------------------------------------------------
            if (!AntBuildWriterUtil.isPomPackaging(project) && extensionWriter.isPlexusProject()) {
                extensionWriter.writePlexusTarget(writer, compileSourceRoots);
                if (!testCompileSourceRoots.isEmpty()) {
                    extensionWriter.writePlexusTestTarget(writer, testCompileSourceRoots);
                }
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
        // Project identification
        // ----------------------------------------------------------------------

        XmlWriterUtil.writeLineBreak(writer, 2, 1);

        writeProperty(writer, "project.groupId", project.getGroupId());
        writeProperty(writer, "project.artifactId", project.getArtifactId());
        writeProperty(writer, "project.version", project.getVersion());
        writeProperty(writer, "spec.version", AntBuildWriterUtil.getSpecificationVersion(project.getVersion()));

        // ----------------------------------------------------------------------
        // Build properties
        // ----------------------------------------------------------------------

        XmlWriterUtil.writeLineBreak(writer, 2, 1);

        writeProperty(
                writer,
                "maven.build.finalName",
                AntBuildWriterUtil.toRelative(
                        project.getBasedir(), project.getBuild().getFinalName()));
        writeProperty(
                writer,
                "maven.build.dir",
                AntBuildWriterUtil.toRelative(
                        project.getBasedir(), project.getBuild().getDirectory()));
        writeProperty(
                writer,
                "maven.build.outputDir",
                "${maven.build.dir}/"
                        + AntBuildWriterUtil.toRelative(
                                new File(project.getBuild().getDirectory()),
                                project.getBuild().getOutputDirectory()));

        if (!project.getCompileSourceRoots().isEmpty()) {
            List<String> compileSourceRoots = project.getCompileSourceRoots();
            for (int i = 0; i < compileSourceRoots.size(); i++) {
                writeProperty(
                        writer,
                        "maven.build.srcDir." + i,
                        AntBuildWriterUtil.toRelative(project.getBasedir(), compileSourceRoots.get(i)));
            }
        }

        if (project.getBuild().getResources() != null) {
            List<Resource> resources = project.getBuild().getResources();
            for (int i = 0; i < resources.size(); i++) {
                writeProperty(
                        writer,
                        "maven.build.resourceDir." + i,
                        AntBuildWriterUtil.toRelative(
                                project.getBasedir(), resources.get(i).getDirectory()));
            }
        }

        writeProperty(
                writer,
                "maven.build.testOutputDir",
                "${maven.build.dir}/"
                        + AntBuildWriterUtil.toRelative(
                                new File(project.getBuild().getDirectory()),
                                project.getBuild().getTestOutputDirectory()));

        if (!project.getTestCompileSourceRoots().isEmpty()) {
            List<String> compileSourceRoots = project.getTestCompileSourceRoots();
            for (int i = 0; i < compileSourceRoots.size(); i++) {
                writeProperty(
                        writer,
                        "maven.build.testDir." + i,
                        AntBuildWriterUtil.toRelative(project.getBasedir(), compileSourceRoots.get(i)));
            }
        }

        if (project.getBuild().getTestResources() != null) {
            List<Resource> resources = project.getBuild().getTestResources();
            for (int i = 0; i < resources.size(); i++) {
                writeProperty(
                        writer,
                        "maven.build.testResourceDir." + i,
                        AntBuildWriterUtil.toRelative(
                                project.getBasedir(), resources.get(i).getDirectory()));
            }
        }

        writeProperty(writer, "maven.test.reports", "${maven.build.dir}/test-reports");

        String reportingOutputDir = (project.getModel().getReporting() != null)
                ? project.getModel().getReporting().getOutputDirectory()
                : new File(project.getBuild().getDirectory(), "site").getAbsolutePath();
        // workaround for MNG-3475
        if (!new File(reportingOutputDir).isAbsolute()) {
            reportingOutputDir = new File(project.getBasedir(), reportingOutputDir).getAbsolutePath();
        }
        writeProperty(
                writer,
                "maven.reporting.outputDirectory",
                "${maven.build.dir}/"
                        + AntBuildWriterUtil.toRelative(
                                new File(project.getBuild().getDirectory()), reportingOutputDir));

        if (extensionWriter.isModelloProject()) {
            writeProperty(writer, "maven.build.mdoDir", extensionWriter.getModelloMdoDir());
            writeProperty(writer, "maven.build.mdoOutputDir", "${maven.build.dir}/generated-sources/modello");
        }

        // ----------------------------------------------------------------------
        // Settings properties
        // ----------------------------------------------------------------------

        XmlWriterUtil.writeLineBreak(writer, 2, 1);

        writeProperty(writer, "maven.repo.local", getLocalRepositoryPath());
        writeProperty(writer, "maven.settings.offline", String.valueOf(settings.isOffline()));
        writeProperty(writer, "maven.settings.interactiveMode", String.valueOf(settings.isInteractiveMode()));

        // ----------------------------------------------------------------------
        // Project metadata and aliases
        // ----------------------------------------------------------------------

        XmlWriterUtil.writeLineBreak(writer, 2, 1);

        writeProperty(writer, "project.name", project.getName());
        writeProperty(writer, "project.description", project.getDescription());
        writeProperty(writer, "project.url", project.getUrl());
        writeProperty(
                writer,
                "project.organization.name",
                project.getOrganization() != null ? project.getOrganization().getName() : null);
        writeProperty(
                writer,
                "project.organization.url",
                project.getOrganization() != null ? project.getOrganization().getUrl() : null);
        writeProperty(writer, "project.inceptionYear", project.getInceptionYear());
        writeProperty(writer, "project.packaging", project.getPackaging());
        writeProperty(writer, "project.basedir", "${basedir}");

        writeProperty(writer, "project.build.finalName", "${maven.build.finalName}");
        writeProperty(writer, "project.build.directory", "${maven.build.dir}");
        writeProperty(writer, "project.build.outputDirectory", "${maven.build.outputDir}");
        writeProperty(writer, "project.build.testOutputDirectory", "${maven.build.testOutputDir}");

        if (project.getBuild().getSourceDirectory() != null) {
            writeProperty(
                    writer,
                    "project.build.sourceDirectory",
                    AntBuildWriterUtil.toRelative(
                            project.getBasedir(), project.getBuild().getSourceDirectory()));
        }

        if (project.getBuild().getTestSourceDirectory() != null) {
            writeProperty(
                    writer,
                    "project.build.testSourceDirectory",
                    AntBuildWriterUtil.toRelative(
                            project.getBasedir(), project.getBuild().getTestSourceDirectory()));
        }

        // ----------------------------------------------------------------------
        // Project properties
        // ----------------------------------------------------------------------

        if (project.getProperties() != null && !project.getProperties().isEmpty()) {
            XmlWriterUtil.writeLineBreak(writer, 2, 1);
            XmlWriterUtil.writeCommentText(writer, "Project properties", 1);
            List<String> propertyNames = new ArrayList<>(project.getProperties().stringPropertyNames());
            Collections.sort(propertyNames);
            for (String name : propertyNames) {
                writeProperty(writer, name, project.getProperties().getProperty(name));
            }
        }

        // ----------------------------------------------------------------------
        // Legacy POM aliases
        // ----------------------------------------------------------------------

        XmlWriterUtil.writeLineBreak(writer, 2, 1);

        writeProperty(writer, "pom.groupId", "${project.groupId}");
        writeProperty(writer, "pom.artifactId", "${project.artifactId}");
        writeProperty(writer, "pom.version", "${project.version}");
        writeProperty(writer, "pom.name", project.getName() != null ? "${project.name}" : null);
        writeProperty(writer, "pom.description", project.getDescription() != null ? "${project.description}" : null);
        writeProperty(writer, "pom.url", project.getUrl() != null ? "${project.url}" : null);
        writeProperty(
                writer,
                "pom.organization.name",
                project.getOrganization() != null && project.getOrganization().getName() != null
                        ? "${project.organization.name}"
                        : null);
        writeProperty(
                writer,
                "pom.organization.url",
                project.getOrganization() != null && project.getOrganization().getUrl() != null
                        ? "${project.organization.url}"
                        : null);
        writeProperty(
                writer, "pom.inceptionYear", project.getInceptionYear() != null ? "${project.inceptionYear}" : null);
        writeProperty(writer, "pom.packaging", project.getPackaging() != null ? "${project.packaging}" : null);
        writeProperty(writer, "pom.basedir", "${project.basedir}");

        writeProperty(writer, "pom.build.finalName", "${project.build.finalName}");
        writeProperty(writer, "pom.build.directory", "${project.build.directory}");
        writeProperty(writer, "pom.build.outputDirectory", "${project.build.outputDirectory}");
        writeProperty(writer, "pom.build.testOutputDirectory", "${project.build.testOutputDirectory}");

        if (project.getBuild().getSourceDirectory() != null) {
            writeProperty(writer, "pom.build.sourceDirectory", "${project.build.sourceDirectory}");
        }

        if (project.getBuild().getTestSourceDirectory() != null) {
            writeProperty(writer, "pom.build.testSourceDirectory", "${project.build.testSourceDirectory}");
        }

        List<CompilerExecution> compilerExecutions = AntBuildWriterUtil.getCompilerExecutions(project);
        int baseVersion = AntBuildWriterUtil.getBaseCompileVersion(project, compilerExecutions);
        Set<Integer> jreVersions = computeMultiReleaseVersions(compilerExecutions, baseVersion, false);

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
     * Compute the set of multi-release JDK versions (&gt; 8) used by the given compiler executions.
     *
     * @param compilerExecutions the compiler executions
     * @param baseVersion the base compile version
     * @param requireSources if true, only executions with non-empty compile source roots are considered
     * @return sorted set of JDK versions
     */
    private Set<Integer> computeMultiReleaseVersions(
            List<CompilerExecution> compilerExecutions, int baseVersion, boolean requireSources) {
        Set<Integer> versions = new TreeSet<>();
        for (CompilerExecution exec : compilerExecutions) {
            if (AntBuildWriterUtil.isMultiReleaseExecution(exec, baseVersion, project)) {
                String ver = exec.getRelease() != null ? exec.getRelease() : exec.getTarget();
                try {
                    int intVer = (int) Double.parseDouble(ver);
                    if (intVer > 8
                            && (!requireSources || !exec.getCompileSourceRoots().isEmpty())) {
                        versions.add(intVer);
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return versions;
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

        for (Artifact artifact : artifacts) {
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

        ExtensionClasspathHelper.writeExtensionClasspaths(
                writer, id, project, extensionWriter, artifactResolverWrapper, injectedArtifacts, artifacts);

        writer.endElement(); // path
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
            writeModuleTasks(writer, "clean");
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
            writeModuleTasks(writer, "compile");
            writer.endElement(); // target
        } else {
            List<CompilerExecution> compilerExecutions = AntBuildWriterUtil.getCompilerExecutions(project);
            int baseVersion = AntBuildWriterUtil.getBaseCompileVersion(project, compilerExecutions);
            Set<Integer> mrVersions = computeMultiReleaseVersions(compilerExecutions, baseVersion, true);

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
            writeModuleTasks(writer, "compile-tests");
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
        writeModuleTasks(writer, "test");
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
            writeModuleTasks(writer, "javadoc");
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
            List<String> depends = new ArrayList<>();
            if (extensionWriter.isSisuProject()) {
                depends.add("sisu");
            }
            if (extensionWriter.isPlexusProject()) {
                depends.add("plexus");
            }
            if (depends.isEmpty()) {
                depends.add("compile");
            }
            depends.add("test");
            writer.addAttribute("depends", String.join(",", depends));
        }
        writer.addAttribute("description", "Package the application");

        if (AntBuildWriterUtil.isPomPackaging(project)) {
            writeModuleTasks(writer, "package");
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

        if (extensionWriter.isModelloProject()) {
            String modelloOutputDir = "${maven.build.mdoOutputDir}";
            if (!isCompileSourceRoot(compileSourceRoots, modelloOutputDir) && !dirs.contains(modelloOutputDir)) {
                dirs.add(modelloOutputDir);
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
            String relDir = dir;
            if (dir.startsWith("${maven.build.dir}")) {
                relDir = dir.replace("${maven.build.dir}", "target");
            } else if (dir.equals("${maven.build.mdoOutputDir}")) {
                relDir = "target/generated-sources/modello";
            }
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
        boolean hasSources =
                !compileSourceRoots.isEmpty() || !extraGeneratedDirs.isEmpty() || !extraStaticDirs.isEmpty();
        if (hasSources) {
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

            String[][] javacBasicOptions = {
                {"encoding", "encoding", null},
                {"nowarn", "showWarnings", "false"},
                {"debug", "debug", "true"},
                {"optimize", "optimize", "false"},
                {"deprecation", "showDeprecation", "true"},
                {"target", "target", "1.8"},
                {"verbose", "verbose", "false"},
                {"fork", "fork", "false"},
                {"memoryMaximumSize", "meminitial", null},
                {"memoryInitialSize", "maxmem", null},
                {"source", "source", "1.8"},
                {"release", "release", "8"},
            };
            for (String[] opt : javacBasicOptions) {
                AntBuildWriterUtil.addWrapAttribute(
                        writer,
                        "javac",
                        opt[0],
                        AntBuildWriterUtil.getMavenCompilerPluginBasicOption(project, opt[1], opt[2]),
                        3);
            }

            if (hasSources) {
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

                if (resource.isFiltering()) {
                    writer.startElement("filterchain");
                    writer.startElement("expandproperties");
                    writer.endElement(); // expandproperties
                    writer.endElement(); // filterchain
                }

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
                    if (artifact.isSnapshot()) {
                        if (repository.getSnapshots() != null
                                && !repository.getSnapshots().isEnabled()) {
                            continue;
                        }
                    } else if (repository.getReleases() != null
                            && !repository.getReleases().isEnabled()) {
                        continue;
                    }
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
     * Put a property in properties defined by a name and a value if value is not null.
     *
     * @param properties not null
     * @param name
     * @param value
     */
    private static void addProperty(Properties properties, String name, String value) {
        if (value != null) {
            properties.put(name, value);
        }
    }

    /**
     * Write an XML property element if value is not null.
     *
     * @param writer not null
     * @param name
     * @param value
     */
    private static void writeProperty(XMLWriter writer, String name, String value) {
        if (value != null) {
            writer.startElement("property");
            writer.addAttribute("name", name);
            writer.addAttribute("value", value);
            writer.endElement(); // property
        }
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

        String joined = Arrays.stream(includes)
                .map(m -> (String) m.get(key))
                .filter(s -> !StringUtils.isEmpty(s))
                .collect(Collectors.joining(","));

        return joined.isEmpty() ? null : joined;
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

    /**
     * Write an Ant subant task for the given target in every reactor module, in reactor order.
     *
     * @param writer {@link XMLWriter}
     * @param taskName the Ant target name to invoke in each module
     */
    private void writeModuleTasks(XMLWriter writer, String taskName) {
        for (String moduleSubPath : getSortedModules()) {
            AntBuildWriterUtil.writeAntTask(writer, project, moduleSubPath, taskName);
        }
    }

    private List<String> getSortedModules() {
        if (project.getModules() == null) {
            return Collections.emptyList();
        }
        List<String> modules = new ArrayList<>(project.getModules());
        if (reactorProjects == null || reactorProjects.isEmpty()) {
            return modules;
        }
        Map<String, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < reactorProjects.size(); i++) {
            MavenProject rp = reactorProjects.get(i);
            if (rp.getBasedir() != null) {
                orderMap.put(canonicalOrAbsolutePath(rp.getBasedir()), i);
            }
        }
        modules.sort(Comparator.comparingInt(m -> {
            File modDir = new File(project.getBasedir(), m);
            return orderMap.getOrDefault(canonicalOrAbsolutePath(modDir), Integer.MAX_VALUE);
        }));
        return modules;
    }

    private static String canonicalOrAbsolutePath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }
}
