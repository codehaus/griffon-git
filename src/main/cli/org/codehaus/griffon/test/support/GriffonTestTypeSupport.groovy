/*
 * Copyright 2009 the original author or authors.
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

package org.codehaus.griffon.test.support

import org.codehaus.griffon.test.GriffonTestType
import org.codehaus.griffon.test.GriffonTestTypeResult
import org.codehaus.griffon.test.GriffonTestTargetPattern
import org.codehaus.griffon.test.event.GriffonTestEventPublisher
import org.codehaus.griffon.test.io.SystemOutAndErrSwapper

import org.springframework.core.io.Resource
import griffon.core.GriffonApplication

/**
 * Provides a convenient base for {@link GriffonTestType} implementations.
 */
abstract class GriffonTestTypeSupport implements GriffonTestType {
    /**
     * The name of this test type
     */
    final String name
    
    /**
     * The path to this type's source, relative to the root of all test source
     */
    final String relativeSourcePath

    /**
     * The test target patterns that should be used to filter the tests to run
     */
    GriffonTestTargetPattern[] testTargetPatterns
    
    /**
     * The location where the type's source was compiled to
     */
    File compiledClassesDir
    
    /**
     * The binding from the build environment
     */
    Binding buildBinding
        
    private ClassLoader testClassLoader
    private File sourceDir
    
    /**
     * Sets the name and relativeSourcePath
     */    
    GriffonTestTypeSupport(String name, String relativeSourcePath) {
        [name: name, relativeSourcePath: relativeSourcePath].each {
            if (!it.value) throw new IllegalArgumentException("$it.key cannot be empty or null")
        }
        
        this.name = name
        this.relativeSourcePath = relativeSourcePath
    }

    /**
     * Override to have the tests for this type require a certain suffix
     * 
     * This implementation returns [""] (i.e. no required suffix)
     */
    protected List<String> getTestSuffixes() { 
        [""] // effectively any suffix
    }

    /**
     * Override to have the tests for this type require a certain file extension
     * 
     * This implementation returns ["groovy", "java"]
     */    
    protected List<String> getTestExtensions() {
        ["groovy", "java"]
    }
    
    /**
     * Sets the appropriate instance variables from the parameters, and calls {@link #doPrepare()}
     */
    int prepare(GriffonTestTargetPattern[] testTargetPatterns, File compiledClassesDir, Binding buildBinding) {
        this.testTargetPatterns = testTargetPatterns
        this.compiledClassesDir = compiledClassesDir
        this.buildBinding = buildBinding
        doPrepare()
    }
    
    /**
     * Do any preparation and return the (approximate) number of tests that will be run.
     * 
     * If a number less than 1 is returned, this test type will not be run.
     * 
     * Typically, implementations with call {@link #getTestClassLoader()} and load the appropriate tests
     * that match the {@code testTargetPatterns}.
     */
    abstract protected int doPrepare()
    
    /**
     * Sets the current thread's contextClassLoader to the {@link #getTestClassLoader() test class loader},
     * calls {@link #doRun(GriffonTestEventPublisher)} and then restores the original contextClassLoader. 
     */
    GriffonTestTypeResult run(GriffonTestEventPublisher eventPublisher) {
        def prevContextClassLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = getTestClassLoader()
    
        try {
            doRun(eventPublisher)
        } finally {
            Thread.currentThread().contextClassLoader = prevContextClassLoader
        }
    }

    /**
     * Performs the tests, and appropriately calls {@link GriffonTestEventPublisher eventPublisher}
     * to communicate the status.
     */
    abstract protected GriffonTestTypeResult doRun(GriffonTestEventPublisher eventPublisher)
    
    /**
     * Called after the tests have completed, regardless of success or not.
     * 
     * This implementation does nothing.
     */
    void cleanup() {}
    
    /**
     * The location of this type's source
     */
    protected File getSourceDir() {
        if (!sourceDir) {
            sourceDir = new File("test/$relativeSourcePath") // TODO: remove this hardcoding of 'test/'
        }
        sourceDir
    }
    
    /**
     * A class loader with class path additions of this type's source dir and compile classed dir.
     * 
     * Note: should not be called before {@link #prepare(GriffonTestTargetPattern[],File,Binding) prepare} is called by
     *       the testing system.
     */
    protected ClassLoader getTestClassLoader() {
        if (!testClassLoader) {
            def classPathAdditions = [getSourceDir()]
            if (compiledClassesDir) classPathAdditions << compiledClassesDir
            testClassLoader = new URLClassLoader(classPathAdditions*.toURI()*.toURL() as URL[], buildBinding.classLoader)
        }
        testClassLoader
    }
    
    /**
     * Finds source based on the {@code testSuffixes} and {@code testExtensions} that match the {@code targetPattern}.
     */
    protected List<File> findSourceFiles(GriffonTestTargetPattern targetPattern) {
        def sourceFiles = []
        def resolveResources = buildBinding['resolveResources']
        testSuffixes.each { suffix ->
            testExtensions.each { extension ->
                def resources = resolveResources("file:${sourceDir.absolutePath}/${targetPattern.filePattern}${suffix}.${extension}".toString())
                sourceFiles.addAll(resources*.file.findAll { it.exists() }.toList())
            }
        }
        sourceFiles
    }
    
    /**
     * Calls {@code body} with the GriffonTestTargetPattern that matched the source, and the File for the source.
     */
    protected void eachSourceFile(Closure body) {
        testTargetPatterns.each { testTargetPattern ->
            findSourceFiles(testTargetPattern).each { sourceFile ->
                body(testTargetPattern, sourceFile)
            }
        }
    }
    
    /**
     * Gets the corresponding class name for a source file of this test type.
     */
    protected String sourceFileToClassName(File sourceFile) {
        def filePath = sourceFile.canonicalPath
        def basePath = getSourceDir().canonicalPath
        
        if (!filePath.startsWith(basePath)) {
            throw new IllegalArgumentException("File path (${filePath}) is not descendent of base path (${basePath}).")
        }

        def relativePath = filePath.substring(basePath.size() + 1)
        def suffixPos = relativePath.lastIndexOf(".")
        relativePath[0..(suffixPos - 1)].replace(File.separatorChar, '.' as char)
    }
    
    /**
     * Convenience method for obtaining the class file for a test class
     */
    protected File sourceFileToClassFile(File sourceFile) {
        new File(compiledClassesDir, sourceFileToClassName(sourceFile).replace(".", "/") + ".class")
    }

    /**
     * Convenience method for obtaining the class file for a test class
     */
    protected Class sourceFileToClass(File sourceFile) {
        loadClass(sourceFileToClassName(sourceFile))
    }
    
    /**
     * Creates swapper with echo parameters based on testOptions.echoOut and testOptions.echoErr in the build binding.
     */
    protected SystemOutAndErrSwapper createSystemOutAndErrSwapper() {
        buildBinding.with {
            new SystemOutAndErrSwapper(testOptions.echoOut == true, testOptions.echoErr == true)
        }
    }
    
    /**
     * Loods the class named by {@code className} using a class loader that can load the test classes, 
     * throwing a RuntimeException if the class can't be loaded.
     */
    protected Class loadClass(String className) {
        try {
            getTestClassLoader().loadClass(className)
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Could not load class in test type '$name'", e)
        }
    }

    /**
     * Will return the application if it is present, but will throw an IllegalStateException
     * if it is not. This should only be called when Griffon is bootstrapped up (i.e. not for unit tests)
     */
    protected GriffonApplication getApplication() {
        if (buildBinding.variables.containsKey("app")) {
            buildBinding.getProperty("app")
        } else if (buildBinding.variables.containsKey("griffonApp")) {
            buildBinding.getProperty("griffonApp")
        } else {
            throw new IllegalStateException("Application requested, but is not present in the build binding")
        }
    }
}
