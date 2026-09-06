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

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;

public class AntWrapper {
    public static void invoke(File antBuild) throws BuildException, IllegalArgumentException {
        if (!antBuild.exists()) {
            throw new IllegalArgumentException("antBuild should exist");
        }
        if (!antBuild.isFile()) {
            throw new IllegalArgumentException("antBuild should be a file");
        }

        Project p = new Project();
        p.setUserProperty("ant.file", antBuild.getAbsolutePath());
        p.setUserProperty("basedir", antBuild.getParentFile().getAbsolutePath());
        p.setUserProperty("build.compiler", "extJavac");

        DefaultLogger consoleLogger = new DefaultLogger();
        consoleLogger.setErrorPrintStream(System.err);
        consoleLogger.setOutputPrintStream(System.out);
        consoleLogger.setMessageOutputLevel(Project.MSG_INFO);
        p.addBuildListener(consoleLogger);

        try {
            p.fireBuildStarted();
            p.init();
            ProjectHelper helper = ProjectHelper.getProjectHelper();
            p.addReference("ant.projectHelper", helper);
            helper.parse(p, antBuild);
            p.executeTarget(p.getDefaultTarget());
            p.fireBuildFinished(null);
        } catch (BuildException e) {
            p.fireBuildFinished(e);
            throw new BuildException("Error in the Ant build file: " + e.getMessage(), e);
        }
    }
}
