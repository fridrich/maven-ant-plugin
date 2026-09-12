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

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.testing.PlexusTest;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.LocalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PlexusTest
public class ArtifactResolverWrapperTest {

    @Inject
    private RepositorySystem repositorySystem;

    @Test
    public void testCreateArtifactDefaultsTypeToJar() {
        ArtifactResolverWrapper wrapper = new ArtifactResolverWrapper(null, null, null, null);

        Artifact artifact = wrapper.createArtifact("group", "artifact", "1.0", null, "tests");

        assertEquals("group", artifact.getGroupId());
        assertEquals("artifact", artifact.getArtifactId());
        assertEquals("1.0", artifact.getVersion());
        assertEquals("jar", artifact.getType());
        assertEquals("tests", artifact.getClassifier());
    }

    @Test
    public void testGetLocalRepositoryDirectoryPrefersExplicitField(@TempDir Path fieldDir, @TempDir Path sessionDir)
            throws Exception {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(
                repositorySystem.newLocalRepositoryManager(session, new LocalRepository(sessionDir.toFile())));

        ArtifactResolverWrapper wrapper =
                new ArtifactResolverWrapper(repositorySystem, session, null, fieldDir.toFile());

        assertEquals(fieldDir.toFile(), wrapper.getLocalRepositoryDirectory());
    }

    @Test
    public void testGetLocalRepositoryDirectoryFallsBackToSession(@TempDir Path sessionDir) throws Exception {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(
                repositorySystem.newLocalRepositoryManager(session, new LocalRepository(sessionDir.toFile())));

        ArtifactResolverWrapper wrapper = new ArtifactResolverWrapper(repositorySystem, session, null, null);

        assertEquals(sessionDir.toFile(), wrapper.getLocalRepositoryDirectory());
    }

    @Test
    public void testGetLocalRepositoryDirectoryNullWithoutFieldOrSession() {
        ArtifactResolverWrapper wrapper = new ArtifactResolverWrapper(null, null, null, null);

        assertNull(wrapper.getLocalRepositoryDirectory());
    }

    @Test
    public void testResolveTransitivelyFailsFastWithoutRepositorySystem() {
        ArtifactResolverWrapper wrapper = new ArtifactResolverWrapper(null, null, null, null);

        assertThrows(IOException.class, () -> wrapper.resolveTransitively("group", "artifact", "1.0"));
    }

    @Test
    public void testGetArtifactAbsolutePathFailsFastWithoutRepositorySystem() {
        ArtifactResolverWrapper wrapper = new ArtifactResolverWrapper(null, null, null, null);

        assertThrows(IOException.class, () -> wrapper.getArtifactAbsolutePath("group", "artifact", "1.0"));
    }

    @Test
    public void testGetLocalArtifactPathUsesLocalRepositoryLayout(@TempDir Path sessionDir) throws Exception {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(
                repositorySystem.newLocalRepositoryManager(session, new LocalRepository(sessionDir.toFile())));

        ArtifactResolverWrapper wrapper = new ArtifactResolverWrapper(repositorySystem, session, null, null);
        Artifact artifact = wrapper.createArtifact("group", "artifact", "1.0", "jar", null);

        String path = wrapper.getLocalArtifactPath(artifact);

        assertTrue(path.contains(new File("group/artifact/1.0").getPath()), "expected repo-layout path, got: " + path);
    }

    @Test
    public void testGetSessionBuildsOneFromLocalRepositoryDirectoryWhenSessionMissing(@TempDir Path localRepoDir)
            throws Exception {
        ArtifactResolverWrapper wrapper =
                new ArtifactResolverWrapper(repositorySystem, null, null, localRepoDir.toFile());
        Artifact artifact = wrapper.createArtifact("group", "artifact", "1.0", "jar", null);

        String path = wrapper.getLocalArtifactPath(artifact);

        assertTrue(path.contains(new File("group/artifact/1.0").getPath()), "expected repo-layout path, got: " + path);
    }

    @Test
    public void testGetLocalArtifactPathNullWithoutSession() {
        ArtifactResolverWrapper wrapper = new ArtifactResolverWrapper(null, null, null, null);
        Artifact artifact = wrapper.createArtifact("group", "artifact", "1.0", "jar", null);

        assertNull(wrapper.getLocalArtifactPath(artifact));
    }
}
