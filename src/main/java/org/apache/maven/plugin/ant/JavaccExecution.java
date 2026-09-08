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

import org.codehaus.plexus.util.xml.Xpp3Dom;

public class JavaccExecution {
    private final String id;
    private final String goal; // "javacc", "jjtree", or "jjtree-javacc"
    private final Xpp3Dom configuration;

    public JavaccExecution(String id, String goal, Xpp3Dom configuration) {
        this.id = id;
        this.goal = goal;
        this.configuration = configuration;
    }

    public String getId() {
        return id;
    }

    public String getGoal() {
        return goal;
    }

    public Xpp3Dom getConfiguration() {
        return configuration;
    }
}
