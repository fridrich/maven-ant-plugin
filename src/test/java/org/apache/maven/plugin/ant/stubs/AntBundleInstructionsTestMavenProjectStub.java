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

public class AntBundleInstructionsTestMavenProjectStub extends AbstractAntTestMavenProjectStub {
    @Override
    public String getProjetPath() {
        return "ant-bundle-instructions-test";
    }

    public AntBundleInstructionsTestMavenProjectStub() {
        super();
        Model model = getModel();
        if (model.getBuild() != null) {
            getBuild().setPlugins(model.getBuild().getPlugins());
        }
    }

    // Already present so the plugin doesn't need to resolve it transitively - not something this
    // in-process test harness can do (no wired Maven session/resolver).
    @Override
    public List getCompileArtifacts() {
        Artifact bndAnt = new DefaultArtifact(
                "biz.aQute.bnd",
                "biz.aQute.bnd.ant",
                VersionRange.createFromVersion("7.4.0"),
                Artifact.SCOPE_COMPILE,
                "jar",
                null,
                new DefaultArtifactHandler("jar"),
                false);
        bndAnt.setFile(new File("biz/aQute/bnd/biz.aQute.bnd.ant/7.4.0/biz.aQute.bnd.ant-7.4.0.jar"));

        List artifacts = new ArrayList(super.getCompileArtifacts());
        artifacts.add(bndAnt);
        return artifacts;
    }
}
