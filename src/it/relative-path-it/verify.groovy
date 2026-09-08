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

import java.io.File
import java.util.Properties

File propsFile = new File( basedir, "maven-build.properties" )
assert propsFile.isFile()

Properties props = new Properties()
propsFile.withInputStream { inStream ->
    props.load( inStream )
}

assert props.getProperty( "maven.build.dir" ) == "target"
assert props.getProperty( "maven.build.srcDir.0" ) == "src/main/java"
assert props.getProperty( "maven.build.testDir.0" ) == "src/test/java"
assert props.getProperty( "maven.build.resourceDir.0" ) == "src/main/resources"
assert props.getProperty( "maven.build.resourceDir.1" ) == "."

return true
