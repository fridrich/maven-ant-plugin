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
import java.util.jar.JarFile
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.codehaus.plexus.util.FileUtils

File build = new File( basedir, "build.xml" )
assert build.isFile()

File mavenBuild = new File( basedir, "maven-build.xml" )
assert mavenBuild.isFile()

String xml = FileUtils.fileRead( mavenBuild, "UTF-8" )
Matcher m0 = Pattern.compile( '<copy\\s+file\\s*=\\s*\\Q"${maven.repo.local}/log4j/log4j/1.2.14/log4j-1.2.14.jar"\\E'
    + '\\s*todir\\s*=\\s*\\Q"${maven.build.dir}/${maven.build.finalName}/WEB-INF/lib"\\E' ).matcher( xml )
assert m0.find()

File mavenBuildProperties = new File( basedir, "maven-build.properties" )
assert mavenBuildProperties.isFile()

File warFile = new File( basedir, "target/ant-webapp-test.war" )
assert warFile.isFile()

JarFile war = new JarFile( warFile )
try
{
    String[] expected = [
        "index.jsp",
        "WEB-INF/web.xml",
        "WEB-INF/test.txt",
        "WEB-INF/classes/test.properties",
        "WEB-INF/classes/org/MyClass.class",
        "WEB-INF/lib/log4j-1.2.14.jar",
        "WEB-INF/lib/maven-model-2.0.6.jar",
        "WEB-INF/lib/plexus-utils-1.4.1.jar"
    ]
    for ( String entry : expected )
    {
        assert war.getEntry( entry ) != null
        int count = 0
        for ( je in war.entries() )
        {
            if ( entry.equals( je.name ) )
            {
                count++
            }
        }
        assert count == 1
    }

    String[] unexpected = [
        "org/MyClass.class"
    ]
    for ( String entry : unexpected )
    {
        assert war.getEntry( entry ) == null
    }
}
finally
{
    war.close()
}

return true
