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

public class AntJavaccTestMavenProjectStub extends AbstractAntTestMavenProjectStub {
    @Override
    public String getProjetPath() {
        return "ant-javacc-test";
    }

    public AntJavaccTestMavenProjectStub() {
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
        Artifact javacc = new DefaultArtifact(
                "net.java.dev.javacc",
                "javacc",
                VersionRange.createFromVersion("7.0.12"),
                Artifact.SCOPE_COMPILE,
                "jar",
                null,
                new DefaultArtifactHandler("jar"),
                false);
        javacc.setFile(new File("net/java/dev/javacc/javacc/7.0.12/javacc-7.0.12.jar"));

        List<Artifact> artifacts = new ArrayList<>(super.getCompileArtifacts());
        artifacts.add(javacc);
        return artifacts;
    }
}
