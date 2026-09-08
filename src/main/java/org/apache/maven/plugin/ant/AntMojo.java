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
import java.util.List;
import java.util.Properties;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Generate Ant build files.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id: AntMojo.java 1640228 2014-11-17 21:20:42Z hboutemy $
 * @todo change this to use the artifact ant tasks instead of :get
 */
@Mojo(name = "ant", requiresDependencyResolution = ResolutionScope.TEST)
public class AntMojo extends AbstractMojo {
    // ----------------------------------------------------------------------
    // Mojo components
    // ----------------------------------------------------------------------

    /**
     * Used for creating and resolving artifacts.
     */
    @Inject
    private RepositorySystem repositorySystem;

    // ----------------------------------------------------------------------
    // Mojo parameters
    // ----------------------------------------------------------------------

    /**
     * The repository system session.
     */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySession;

    /**
     * The project to create a build for.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The remote repositories where artifacts are located.
     */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepositories;

    /**
     * The current user system settings for use in Maven.
     */
    @Parameter(defaultValue = "${settings}", readonly = true, required = true)
    private Settings settings;

    /**
     * Whether or not to overwrite the <code>build.xml</code> file.
     */
    @Parameter(property = "overwrite", defaultValue = "false")
    private boolean overwrite;

    /**
     * The current Maven session.
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * {@inheritDoc}
     */
    public void execute() throws MojoExecutionException {
        RepositorySystemSession repoSession = repositorySession;
        if (repoSession == null && session != null) {
            repoSession = session.getRepositorySession();
        }

        File localRepoDir = null;
        if (repoSession != null && repoSession.getLocalRepository() != null) {
            localRepoDir = repoSession.getLocalRepository().getBasedir();
        } else if (session != null
                && session.getRequest() != null
                && session.getRequest().getLocalRepositoryPath() != null) {
            localRepoDir = session.getRequest().getLocalRepositoryPath();
        }

        ArtifactResolverWrapper artifactResolverWrapper =
                ArtifactResolverWrapper.getInstance(repositorySystem, repoSession, remoteRepositories, localRepoDir);

        Properties executionProperties = null;
        if (session != null) {
            executionProperties = new Properties();
            if (session.getSystemProperties() != null) {
                executionProperties.putAll(session.getSystemProperties());
            }
            if (session.getUserProperties() != null) {
                executionProperties.putAll(session.getUserProperties());
            }
        }
        List<MavenProject> reactorProjects = (session != null) ? session.getProjects() : null;

        AntBuildWriter antBuildWriter = new AntBuildWriter(
                project, artifactResolverWrapper, settings, overwrite, executionProperties, reactorProjects);

        try {
            antBuildWriter.writeBuildXmls();
            antBuildWriter.writeBuildProperties();
        } catch (IOException e) {
            throw new MojoExecutionException("Error building Ant script: " + e.getMessage(), e);
        }

        getLog().info("Wrote Ant project for " + project.getArtifactId() + " to "
                + project.getBasedir().getAbsolutePath());
    }
}
