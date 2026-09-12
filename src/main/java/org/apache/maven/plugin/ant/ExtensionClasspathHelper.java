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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.XMLWriter;

/**
 * Resolves and writes classpaths needed for build extensions (Sisu, Bnd, JavaCC, JFlex, CUP).
 */
class ExtensionClasspathHelper {

    private ExtensionClasspathHelper() {}

    static void writeExtensionClasspaths(
            XMLWriter writer,
            String id,
            MavenProject project,
            AntExtensionWriter extensionWriter,
            ArtifactResolverWrapper artifactResolverWrapper,
            Set<Artifact> injectedArtifacts,
            Collection<Artifact> artifacts)
            throws IOException {
        if (!"build.classpath".equals(id) && !"build.test.classpath".equals(id)) {
            return;
        }

        boolean sisuInjectPresent = false;
        boolean asmPresent = false;
        boolean bndAntPresent = false;
        boolean javaccPresent = false;
        for (Artifact artifact : artifacts) {
            if ("org.eclipse.sisu.inject".equals(artifact.getArtifactId())) {
                sisuInjectPresent = true;
            }
            if ("asm".equals(artifact.getArtifactId()) && "org.ow2.asm".equals(artifact.getGroupId())) {
                asmPresent = true;
            }
            if ("biz.aQute.bnd.ant".equals(artifact.getArtifactId())) {
                bndAntPresent = true;
            }
            if ("javacc".equals(artifact.getArtifactId())) {
                javaccPresent = true;
            }
        }

        if (extensionWriter.isSisuProject() && !sisuInjectPresent) {
            resolveAndAdd(
                    writer,
                    artifactResolverWrapper,
                    injectedArtifacts,
                    "org.eclipse.sisu",
                    "org.eclipse.sisu.inject",
                    extensionWriter.getSisuVersion());
        }

        if (extensionWriter.isSisuProject() && !asmPresent) {
            Artifact asmArtifact = null;
            if (project.getArtifacts() != null) {
                for (Artifact art : project.getArtifacts()) {
                    if ("org.ow2.asm".equals(art.getGroupId()) && "asm".equals(art.getArtifactId())) {
                        asmArtifact = art;
                        break;
                    }
                }
            }
            if (asmArtifact != null) {
                writer.startElement("pathelement");
                writer.addAttribute(
                        "location", "${maven.repo.local}/" + artifactResolverWrapper.getLocalArtifactPath(asmArtifact));
                writer.endElement(); // pathelement
            } else {
                try {
                    Set<Artifact> resolved =
                            artifactResolverWrapper.resolveTransitively("org.ow2.asm", "asm", "9.10.1");
                    addPathelements(writer, resolved, artifactResolverWrapper, injectedArtifacts);
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        if (extensionWriter.isDependencyUnpackProject()) {
            for (DependencyUnpackItem item : extensionWriter.getDependencyUnpackItems()) {
                try {
                    Artifact art = artifactResolverWrapper.createArtifact(
                            item.getGroupId(),
                            item.getArtifactId(),
                            item.getVersion(),
                            item.getType(),
                            item.getClassifier());
                    if (art != null) {
                        injectedArtifacts.add(art);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        if (extensionWriter.isBndProject() && !bndAntPresent) {
            resolveBndClasspaths(writer, artifactResolverWrapper, injectedArtifacts);
        }

        if (extensionWriter.isJavaccProject() && !javaccPresent) {
            resolveAndAdd(
                    writer, artifactResolverWrapper, injectedArtifacts, "net.java.dev.javacc", "javacc", "7.0.12");
        }

        if (extensionWriter.isJflexProject()) {
            try {
                Set<Artifact> resolved = new HashSet<>();
                resolved.addAll(artifactResolverWrapper.resolveTransitively(
                        "de.jflex", "jflex", extensionWriter.getJflexVersion()));
                resolved.addAll(artifactResolverWrapper.resolveTransitively(
                        "com.github.vbmacher", "java-cup-runtime", "11b-20160615-1"));
                addPathelements(writer, resolved, artifactResolverWrapper, injectedArtifacts);
            } catch (Exception e) {
                // ignore
            }
        }

        if (extensionWriter.isCupProject()) {
            resolveAndAdd(
                    writer,
                    artifactResolverWrapper,
                    injectedArtifacts,
                    "com.github.vbmacher",
                    "java-cup",
                    extensionWriter.getCupVersion());
        }
    }

    private static void resolveAndAdd(
            XMLWriter writer,
            ArtifactResolverWrapper artifactResolverWrapper,
            Set<Artifact> injectedArtifacts,
            String groupId,
            String artifactId,
            String version) {
        try {
            Set<Artifact> resolved = artifactResolverWrapper.resolveTransitively(groupId, artifactId, version);
            addPathelements(writer, resolved, artifactResolverWrapper, injectedArtifacts);
        } catch (Exception e) {
            // ignore
        }
    }

    private static void addPathelements(
            XMLWriter writer,
            Set<Artifact> artifacts,
            ArtifactResolverWrapper artifactResolverWrapper,
            Set<Artifact> injectedArtifacts) {
        injectedArtifacts.addAll(artifacts);
        for (Artifact art : artifacts) {
            writer.startElement("pathelement");
            writer.addAttribute("location", "${maven.repo.local}/" + artifactResolverWrapper.getLocalArtifactPath(art));
            writer.endElement(); // pathelement
        }
    }

    private static void resolveBndClasspaths(
            XMLWriter writer, ArtifactResolverWrapper artifactResolverWrapper, Set<Artifact> injectedArtifacts)
            throws IOException {
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
            addPathelements(writer, resolved, artifactResolverWrapper, injectedArtifacts);
        } catch (IOException e) {
            // ignore
        }
    }
}
