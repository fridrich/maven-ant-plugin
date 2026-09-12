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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * Wrapper object to resolve artifact.
 *
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 * @version $Id: ArtifactResolverWrapper.java 1645084 2014-12-12 22:28:31Z khmarbaise $
 */
public class ArtifactResolverWrapper {
    /**
     * Used for creating and resolving artifacts
     */
    private RepositorySystem repositorySystem;

    /**
     * The repository system session
     */
    private RepositorySystemSession repositorySession;

    /**
     * The local repository base directory
     */
    private File localRepositoryDirectory;

    /**
     * The remote repositories where artifacts are located
     */
    private List<RemoteRepository> remoteRepositories;

    /**
     * @param repositorySystem
     * @param repositorySession
     * @param remoteRepositories
     * @param localRepositoryDirectory
     */
    /**
     * @param repositorySystem
     * @param repositorySession
     * @param remoteRepositories
     * @param localRepositoryDirectory
     */
    public ArtifactResolverWrapper(
            RepositorySystem repositorySystem,
            RepositorySystemSession repositorySession,
            List<RemoteRepository> remoteRepositories,
            File localRepositoryDirectory) {
        this.repositorySystem = repositorySystem;
        this.repositorySession = repositorySession;
        this.remoteRepositories = remoteRepositories != null ? remoteRepositories : Collections.emptyList();
        this.localRepositoryDirectory = localRepositoryDirectory;
    }

    private RepositorySystemSession getSession() {
        if (repositorySession != null) {
            return repositorySession;
        }
        File localRepo = getLocalRepositoryDirectory();
        if (repositorySystem != null && localRepo != null) {
            DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
            LocalRepository localRepository = new LocalRepository(localRepo);
            session.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(session, localRepository));
            return session;
        }
        return null;
    }

    /**
     * Resolve an artifact transitively and return all resolved dependencies.
     *
     * @param groupId The groupId.
     * @param artifactId The artifactId.
     * @param version The version.
     * @return set of resolved artifacts.
     * @throws IOException if resolution fails.
     */
    public Set<Artifact> resolveTransitively(String groupId, String artifactId, String version) throws IOException {
        RepositorySystemSession session = getSession();
        if (repositorySystem == null || session == null) {
            throw new IOException("RepositorySystem or session not available for resolution");
        }

        org.eclipse.aether.artifact.Artifact aetherArtifact = new DefaultArtifact(groupId, artifactId, "jar", version);
        Dependency dependency = new Dependency(aetherArtifact, JavaScopes.COMPILE);
        CollectRequest collectRequest = new CollectRequest(dependency, remoteRepositories);
        DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);

        try {
            DependencyResult dependencyResult = repositorySystem.resolveDependencies(session, dependencyRequest);
            Set<Artifact> allResolved = new HashSet<>();
            for (ArtifactResult artifactResult : dependencyResult.getArtifactResults()) {
                allResolved.add(RepositoryUtils.toArtifact(artifactResult.getArtifact()));
            }
            return allResolved;
        } catch (DependencyResolutionException e) {
            throw new IOException("Unable to transitively resolve: " + groupId + ":" + artifactId + ":" + version, e);
        }
    }

    /**
     * @return {@link #localRepositoryDirectory}
     */
    public File getLocalRepositoryDirectory() {
        if (localRepositoryDirectory != null) {
            return localRepositoryDirectory;
        }
        if (repositorySession != null) {
            if (repositorySession.getLocalRepositoryManager() != null
                    && repositorySession.getLocalRepositoryManager().getRepository() != null) {
                return repositorySession
                        .getLocalRepositoryManager()
                        .getRepository()
                        .getBasedir();
            }
            if (repositorySession.getLocalRepository() != null) {
                return repositorySession.getLocalRepository().getBasedir();
            }
        }
        return null;
    }

    /**
     * Return the artifact path in the local repository for an artifact defined by its <code>groupId</code>,
     * its <code>artifactId</code> and its <code>version</code>.
     *
     * @param groupId The groupId.
     * @param artifactId The artifactId.
     * @param version The version.
     * @return the locale artifact path
     * @throws IOException if any
     */
    public String getArtifactAbsolutePath(String groupId, String artifactId, String version) throws IOException {
        RepositorySystemSession session = getSession();
        if (repositorySystem == null || session == null) {
            throw new IOException("RepositorySystem or session not available for resolution");
        }

        org.eclipse.aether.artifact.Artifact aetherArtifact = new DefaultArtifact(groupId, artifactId, "jar", version);
        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(aetherArtifact);
        request.setRepositories(remoteRepositories);

        try {
            ArtifactResult result = repositorySystem.resolveArtifact(session, request);
            return result.getArtifact().getFile().getAbsolutePath();
        } catch (ArtifactResolutionException e) {
            throw new IOException("Unable to resolve artifact: " + groupId + ":" + artifactId + ":" + version, e);
        }
    }

    /**
     * Gets the path to the specified artifact relative to the local repository's base directory. Note that this method
     * does not actually resolve the artifact, it merely calculates the path at which the artifact is or would be stored
     * in the local repository.
     *
     * @param artifact The artifact whose path should be determined, must not be <code>null</code>.
     * @return The path to the artifact, never <code>null</code>.
     */
    public String getLocalArtifactPath(Artifact artifact) {
        /*
         * NOTE: Don't use Artifact.getFile() here because this method could return the path to a JAR from the build
         * output, e.g. ".../target/some-0.1.jar". The other special case are system-scope artifacts that reside
         * somewhere outside of the local repository.
         */
        RepositorySystemSession session = getSession();
        if (session != null && session.getLocalRepositoryManager() != null) {
            org.eclipse.aether.artifact.Artifact aetherArtifact = RepositoryUtils.toArtifact(artifact);
            return session.getLocalRepositoryManager().getPathForLocalArtifact(aetherArtifact);
        }
        return null;
    }

    public Artifact createArtifact(String groupId, String artifactId, String version, String type, String classifier) {
        org.eclipse.aether.artifact.Artifact aetherArtifact = new org.eclipse.aether.artifact.DefaultArtifact(
                groupId, artifactId, classifier, type != null ? type : "jar", version);
        return RepositoryUtils.toArtifact(aetherArtifact);
    }
}
