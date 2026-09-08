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
package org.apache.maven.plugin.ant.stubs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Model;

public class AntSisuTestMavenProjectStub extends AbstractAntTestMavenProjectStub {
    @Override
    public String getProjetPath() {
        return "ant-sisu-test";
    }

    public AntSisuTestMavenProjectStub() {
        super();
        Model model = getModel();
        if (model.getBuild() != null) {
            getBuild().setPlugins(model.getBuild().getPlugins());
        }
    }

    // Already present so the plugin doesn't need to resolve it transitively - not something this
    // in-process test harness can do (no wired Maven session/resolver).
    @Override
    public List<Artifact> getCompileArtifacts() {
        Artifact sisuInject = new DefaultArtifact(
                "org.eclipse.sisu",
                "org.eclipse.sisu.inject",
                VersionRange.createFromVersion("1.0.1"),
                Artifact.SCOPE_COMPILE,
                "jar",
                null,
                new DefaultArtifactHandler("jar"),
                false);
        sisuInject.setFile(
                new File("org/eclipse/sisu/org.eclipse.sisu.inject/1.0.1/org.eclipse.sisu.inject-1.0.1.jar"));

        Artifact javaxInject = new DefaultArtifact(
                "javax.inject",
                "javax.inject",
                VersionRange.createFromVersion("1"),
                Artifact.SCOPE_COMPILE,
                "jar",
                null,
                new DefaultArtifactHandler("jar"),
                false);
        javaxInject.setFile(new File("javax/inject/javax.inject/1/javax.inject-1.jar"));

        List<Artifact> artifacts = new ArrayList<>(super.getCompileArtifacts());
        artifacts.add(sisuInject);
        artifacts.add(javaxInject);
        return artifacts;
    }

    @Override
    public List<Artifact> getTestArtifacts() {
        List<Artifact> artifacts = new ArrayList<>(getCompileArtifacts());
        artifacts.addAll(super.getTestArtifacts());
        return artifacts;
    }
}
