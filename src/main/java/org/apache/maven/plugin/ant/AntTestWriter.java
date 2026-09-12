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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.XMLWriter;
import org.codehaus.plexus.util.xml.XmlWriterUtil;

/**
 * Write Ant test execution targets for supported test frameworks.
 */
public class AntTestWriter {
    private final MavenProject project;
    private final AntExtensionWriter extensionWriter;

    /**
     * @param project {@link MavenProject}
     */
    public AntTestWriter(MavenProject project) {
        this(project, null);
    }

    /**
     * @param project {@link MavenProject}
     * @param extensionWriter {@link AntExtensionWriter}
     */
    public AntTestWriter(MavenProject project, AntExtensionWriter extensionWriter) {
        this.project = project;
        this.extensionWriter = extensionWriter;
    }

    /**
     * Write test target and supporting targets for non-POM projects.
     *
     * @param writer {@link XMLWriter}
     * @param testCompileSourceRoots source roots
     * @param testIncludes pattern list of includes
     * @param testExcludes pattern list of excludes
     * @throws IOException if any
     */
    public void writeTestTargets(
            XMLWriter writer, List<String> testCompileSourceRoots, List<String> testIncludes, List<String> testExcludes)
            throws IOException {
        AntBuildWriterUtil.TestFramework framework = AntBuildWriterUtil.getTestFramework(project);

        List<String> testDepends = new ArrayList<>();
        testDepends.add("compile-tests");
        if (extensionWriter != null) {
            if (extensionWriter.isSisuProject()) {
                testDepends.add("sisu");
                if (!testCompileSourceRoots.isEmpty()) {
                    testDepends.add("sisu-test");
                }
            }
            if (extensionWriter.isPlexusProject()) {
                testDepends.add("plexus");
                if (!testCompileSourceRoots.isEmpty()) {
                    testDepends.add("plexus-test");
                }
            }
        }
        testDepends.add("junit-missing");

        writer.startElement("target");
        writer.addAttribute("name", "test");
        AntBuildWriterUtil.addWrapAttribute(writer, "target", "depends", String.join(", ", testDepends), 2);
        AntBuildWriterUtil.addWrapAttribute(writer, "target", "unless", "junit.skipped", 2);
        AntBuildWriterUtil.addWrapAttribute(writer, "target", "description", "Run the test cases", 2);

        if (!testCompileSourceRoots.isEmpty()) {
            writeTestRunnerCall(writer, framework);
        }
        writer.endElement(); // target

        XmlWriterUtil.writeLineBreak(writer, 2, 1);

        if (!testCompileSourceRoots.isEmpty()) {
            writeTestRunnerTarget(writer, framework, testCompileSourceRoots, testIncludes, testExcludes);
            XmlWriterUtil.writeLineBreak(writer, 2, 1);
        }

        writer.startElement("target");
        writer.addAttribute("name", "test-junit-present");

        writer.startElement("available");
        writer.addAttribute("classname", AntBuildWriterUtil.getTestFrameworkClassName(project));
        writer.addAttribute("property", "junit.present");
        writer.addAttribute("classpathref", "build.test.classpath");
        writer.endElement(); // available

        writer.endElement(); // target

        XmlWriterUtil.writeLineBreak(writer, 2, 1);

        writer.startElement("target");
        writer.addAttribute("name", "test-junit-status");
        AntBuildWriterUtil.addWrapAttribute(writer, "target", "depends", "test-junit-present", 2);
        writer.startElement("condition");
        writer.addAttribute("property", "junit.missing");
        writer.startElement("and");
        writer.startElement("isfalse");
        writer.addAttribute("value", "${junit.present}");
        writer.endElement(); // isfalse
        writer.startElement("isfalse");
        writer.addAttribute("value", "${maven.test.skip}");
        writer.endElement(); // isfalse
        writer.endElement(); // and
        writer.endElement(); // condition
        writer.startElement("condition");
        writer.addAttribute("property", "junit.skipped");
        writer.startElement("or");
        writer.startElement("isfalse");
        writer.addAttribute("value", "${junit.present}");
        writer.endElement(); // isfalse
        writer.startElement("istrue");
        writer.addAttribute("value", "${maven.test.skip}");
        writer.endElement(); // istrue
        writer.endElement(); // or
        writer.endElement(); // condition
        writer.endElement(); // target

        XmlWriterUtil.writeLineBreak(writer, 2, 1);

        writer.startElement("target");
        writer.addAttribute("name", "junit-missing");
        AntBuildWriterUtil.addWrapAttribute(writer, "target", "depends", "test-junit-status", 2);
        AntBuildWriterUtil.addWrapAttribute(writer, "target", "if", "junit.missing", 2);

        // CHECKSTYLE_OFF: MagicNumber
        writer.startElement("echo");
        writer.writeText(StringUtils.repeat("=", 35) + " WARNING " + StringUtils.repeat("=", 35));
        writer.endElement(); // echo

        writer.startElement("echo");
        // CHECKSTYLE_OFF: LineLength
        writer.writeText(
                " JUnit is not present in the test classpath or your $ANT_HOME/lib directory. Tests not executed.");
        // CHECKSTYLE_ON: LineLength
        writer.endElement(); // echo

        writer.startElement("echo");
        writer.writeText(StringUtils.repeat("=", 79));
        writer.endElement(); // echo
        // CHECKSTYLE_ON: MagicNumber

        writer.endElement(); // target
    }

    private void writeTestRunnerCall(XMLWriter writer, AntBuildWriterUtil.TestFramework framework) {
        writer.startElement("available");
        writer.addAttribute("classname", AntBuildWriterUtil.getTestRunnerClassName(framework));
        writer.addAttribute("property", AntBuildWriterUtil.getTestRunnerPresentProperty(framework));
        writer.endElement(); // available

        writer.startElement("antcall");
        writer.addAttribute("target", AntBuildWriterUtil.getTestRunnerTargetName(framework));
        writer.endElement(); // antcall
    }

    private void writeTestRunnerTarget(
            XMLWriter writer,
            AntBuildWriterUtil.TestFramework framework,
            List<String> testCompileSourceRoots,
            List<String> testIncludes,
            List<String> testExcludes)
            throws IOException {
        switch (framework) {
            case JUNIT3:
            case JUNIT4:
                writeJunit(writer, testCompileSourceRoots, testIncludes, testExcludes);
                break;
            case TESTNG:
                writeTestNG(writer, testIncludes, testExcludes);
                break;
            case JUNIT5:
            default:
                writeJunitLauncher(writer, testIncludes, testExcludes);
                break;
        }
    }

    private void writeTestClasspath(XMLWriter writer) {
        writer.startElement("classpath");
        writer.startElement("path");
        writer.addAttribute("refid", "build.test.classpath");
        writer.endElement(); // path
        writer.startElement("pathelement");
        writer.addAttribute("location", "${maven.build.outputDir}");
        writer.endElement(); // pathelement
        writer.startElement("pathelement");
        writer.addAttribute("location", "${maven.build.testOutputDir}");
        writer.endElement(); // pathelement
        writer.startElement("pathelement");
        writer.addAttribute("path", "${java.class.path}");
        writer.endElement(); // pathelement
        writer.endElement(); // classpath
    }

    private void writeTestIncludesPatternCondition(XMLWriter writer) {
        writer.startElement("condition");
        writer.addAttribute("property", "maven.test.includesPattern");
        writer.addAttribute("value", "**/${test}.class");
        writer.startElement("isset");
        writer.addAttribute("property", "test");
        writer.endElement(); // isset
        writer.endElement(); // condition
    }

    private void writeTestClassIncludesExcludes(
            XMLWriter writer, List<String> testIncludes, List<String> testExcludes) {
        writer.startElement("include");
        writer.addAttribute("name", "${maven.test.includesPattern}");
        writer.addAttribute("if", "test");
        writer.endElement(); // include

        for (String incl : testIncludes) {
            writer.startElement("include");
            String inclPattern = incl.replace(".java", ".class");
            writer.addAttribute("name", inclPattern);
            writer.addAttribute("unless", "test");
            writer.endElement(); // include
        }

        for (String excl : testExcludes) {
            writer.startElement("exclude");
            String exclPattern = excl.replace(".java", ".class");
            writer.addAttribute("name", exclPattern);
            writer.endElement(); // exclude
        }
    }

    private void writeTestFilesets(
            XMLWriter writer, List<String> testCompileSourceRoots, List<String> includes, List<String> excludes) {
        for (int i = 0; i < testCompileSourceRoots.size(); i++) {
            writer.startElement("fileset");
            writer.addAttribute("dir", "${maven.build.testDir." + i + "}");
            AntBuildWriterUtil.writeIncludesExcludes(writer, includes, excludes);
            writer.endElement(); // fileset
        }
    }

    private void writeJunit(
            XMLWriter writer, List<String> testCompileSourceRoots, List<String> testIncludes, List<String> testExcludes)
            throws IOException {
        writer.startElement("target");
        writer.addAttribute("name", "-run-tests-junit");
        writer.addAttribute("if", "junit.task.present");

        writer.startElement("mkdir");
        writer.addAttribute("dir", "${maven.test.reports}");
        writer.endElement(); // mkdir

        writer.startElement("junit");
        writer.addAttribute("printSummary", "yes");
        writer.addAttribute("haltonerror", "true");
        writer.addAttribute("haltonfailure", "true");
        writer.addAttribute("fork", "true");
        writer.addAttribute("dir", ".");

        writer.startElement("sysproperty");
        writer.addAttribute("key", "basedir");
        writer.addAttribute("value", ".");
        writer.endElement(); // sysproperty

        writer.startElement("formatter");
        writer.addAttribute("type", "xml");
        writer.endElement(); // formatter

        writer.startElement("formatter");
        writer.addAttribute("type", "plain");
        writer.addAttribute("usefile", "false");
        writer.endElement(); // formatter

        writeTestClasspath(writer);

        writer.startElement("batchtest");
        writer.addAttribute("todir", "${maven.test.reports}");
        writer.addAttribute("unless", "test");

        writeTestFilesets(writer, testCompileSourceRoots, testIncludes, testExcludes);

        writer.endElement(); // batchtest

        writer.startElement("batchtest");
        writer.addAttribute("todir", "${maven.test.reports}");
        writer.addAttribute("if", "test");

        List<String> includes = Collections.singletonList("**/${test}.java");
        writeTestFilesets(writer, testCompileSourceRoots, includes, testExcludes);

        writer.endElement(); // batchtest

        writer.endElement(); // junit

        writer.endElement(); // target
    }

    private void writeJunitLauncher(XMLWriter writer, List<String> testIncludes, List<String> testExcludes)
            throws IOException {
        writer.startElement("target");
        writer.addAttribute("name", "-run-tests-junitlauncher");
        writer.addAttribute("if", "junitlauncher.present");

        writer.startElement("mkdir");
        writer.addAttribute("dir", "${maven.test.reports}");
        writer.endElement(); // mkdir

        writeTestIncludesPatternCondition(writer);

        writer.startElement("junitlauncher");
        writer.addAttribute("haltOnFailure", "true");
        writer.addAttribute("printSummary", "true");

        writeTestClasspath(writer);

        writer.startElement("testclasses");
        writer.addAttribute("outputdir", "${maven.test.reports}");

        writer.startElement("fork");
        writer.addAttribute("dir", "${basedir}");
        writer.startElement("sysproperty");
        writer.addAttribute("key", "basedir");
        writer.addAttribute("value", "${basedir}");
        writer.endElement(); // sysproperty
        writer.endElement(); // fork

        writer.startElement("fileset");
        writer.addAttribute("dir", "${maven.build.testOutputDir}");

        writeTestClassIncludesExcludes(writer, testIncludes, testExcludes);

        writer.endElement(); // fileset

        writer.startElement("listener");
        writer.addAttribute("type", "legacy-xml");
        writer.addAttribute("sendSysOut", "true");
        writer.addAttribute("sendSysErr", "true");
        writer.endElement(); // listener

        writer.startElement("listener");
        writer.addAttribute("type", "legacy-plain");
        writer.addAttribute("sendSysOut", "true");
        writer.addAttribute("sendSysErr", "true");
        writer.endElement(); // listener

        writer.endElement(); // testclasses

        writer.endElement(); // junitlauncher

        writer.endElement(); // target
    }

    private void writeTestNG(XMLWriter writer, List<String> testIncludes, List<String> testExcludes)
            throws IOException {
        writer.startElement("target");
        writer.addAttribute("name", "-run-tests-testng");
        writer.addAttribute("if", "testng.present");

        writer.startElement("taskdef");
        writer.addAttribute("name", "testng");
        writer.addAttribute("classname", "org.testng.TestNGAntTask");
        writer.addAttribute("classpathref", "build.test.classpath");
        writer.endElement(); // taskdef

        writer.startElement("mkdir");
        writer.addAttribute("dir", "${maven.test.reports}");
        writer.endElement(); // mkdir

        writeTestIncludesPatternCondition(writer);

        writer.startElement("testng");
        writer.addAttribute("outputDir", "${maven.test.reports}");
        writer.addAttribute("haltOnFailure", "true");

        writeTestClasspath(writer);

        writer.startElement("classfileset");
        writer.addAttribute("dir", "${maven.build.testOutputDir}");

        writeTestClassIncludesExcludes(writer, testIncludes, testExcludes);

        writer.endElement(); // classfileset

        writer.endElement(); // testng

        writer.endElement(); // target
    }
}
