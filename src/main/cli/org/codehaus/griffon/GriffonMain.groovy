/*
 * Copyright 2008-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author Danno Ferrin
 */

package org.codehaus.griffon

import org.codehaus.groovy.tools.GroovyStarter

def ant = new AntBuilder()
ant.property(environment:"env")
griffonHome = ant.antProject.properties."env.GRIFFON_HOME"

if(!griffonHome) {
	println "Environment variable GRIFFON_HOME must be set to the location of your Griffon install"
	return
}
System.setProperty("groovy.starter.conf", "${griffonHome}/conf/groovy-starter.conf")
System.setProperty("griffon.home", griffonHome)
GroovyStarter.rootLoader(["--main", "org.codehaus.griffon.cli.GriffonScriptRunner", "run-app"] as String[])	
