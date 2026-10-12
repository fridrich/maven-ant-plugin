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
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.XMLWriter;

/**
 * Paths of reactor siblings, relative to the module being written.
 */
final class ReactorPaths {
    private ReactorPaths() {}

    /** Sibling output dir suffix for a dependency on it; test-jar maps to test-classes. */
    static String classesDir(Artifact artifact) {
        return "tests".equals(artifact.getClassifier()) || "test-jar".equals(artifact.getType())
                ? "/target/test-classes"
                : "/target/classes";
    }

    /** Dirs holding what the sibling's artifact with this classifier would contain. */
    static List<String> siblingDirs(MavenProject project, MavenProject sibling, String classifier) {
        String rel = AntBuildWriterUtil.toRelative(
                project.getBasedir(), sibling.getBasedir().getAbsolutePath());
        List<String> roots = "sources".equals(classifier)
                ? sibling.getCompileSourceRoots()
                : "test-sources".equals(classifier) ? sibling.getTestCompileSourceRoots() : null;
        List<String> dirs = new ArrayList<>();
        if (roots == null) {
            dirs.add(rel + ("tests".equals(classifier) ? "/target/test-classes" : "/target/classes"));
            return dirs;
        }
        String buildDir = sibling.getBuild().getDirectory();
        for (String root : roots) {
            // generated roots don't exist before the sibling is built
            if (buildDir == null || !root.startsWith(buildDir)) {
                dirs.add(AntBuildWriterUtil.toRelative(project.getBasedir(), root));
            }
        }
        return dirs;
    }

    /**
     * Module path for a standalone build: only compile-scope deps. All of the shared lib dir would
     * drag every automatic module into resolution and clash on split packages.
     */
    static void writeStandaloneModulePath(
            XMLWriter writer, MavenProject project, List<MavenProject> reactorProjects, String libRelPath) {
        List<String> patterns = new ArrayList<>();
        for (Artifact a : project.getCompileArtifacts()) {
            MavenProject sibling = AntBuildWriterUtil.findReactorProject(a, reactorProjects);
            if (sibling != null) {
                writer.startElement("pathelement");
                writer.addAttribute(
                        "location",
                        AntBuildWriterUtil.toRelative(
                                        project.getBasedir(),
                                        sibling.getBasedir().getAbsolutePath())
                                + classesDir(a));
                writer.endElement(); // pathelement
            } else {
                patterns.add("**/" + a.getArtifactId() + "*.jar");
            }
        }
        if (patterns.isEmpty()) {
            return; // patternless fileset would mean all of lib
        }
        writer.startElement("fileset");
        writer.addAttribute("dir", libRelPath);
        for (String pattern : patterns) {
            writer.startElement("include");
            writer.addAttribute("name", pattern);
            writer.endElement(); // include
        }
        writer.endElement(); // fileset
    }

    static String libRelPath(MavenProject project, File rootProjectDir) {
        File rootLib = (rootProjectDir != null) ? new File(rootProjectDir, "lib") : new File("lib");
        return (project.getBasedir() != null)
                ? AntBuildWriterUtil.toRelative(project.getBasedir(), rootLib.getAbsolutePath())
                : "lib";
    }
}
