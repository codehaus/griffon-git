/* 
 * Copyright 2004-2011 the original author or authors.
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
package griffon.util

import groovy.util.slurpersupport.GPathResult

import java.util.concurrent.ConcurrentHashMap

import org.apache.commons.lang.ArrayUtils

import org.codehaus.griffon.plugins.CompositePluginDescriptorReader
import org.codehaus.griffon.plugins.GriffonPlugin
import org.codehaus.griffon.plugins.GriffonPluginInfo
import org.codehaus.griffon.plugins.GriffonPluginManager
import org.codehaus.griffon.plugins.PluginInfo

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

import org.codehaus.gant.GantBinding

/**
 * Uses the project BuildSettings object to discover information about the installed plugin
 * such as the jar files they provide, the plugin descriptors and so on.
 *
 * @author Graeme Rocher (Grails 1.2)
 */
class PluginBuildSettings {
    /**
     * Resources to be excluded from the final packaged plugin. Defined as Ant paths.
     */
    public static final EXCLUDED_RESOURCES = [
        "griffon-app/conf/Application.groovy",
        "griffon-app/conf/Builder.groovy",
        "griffon-app/conf/Config.groovy",
        "griffon-app/conf/BuildConfig.groovy",
        "griffon-app/conf/metainf/**",
        "**/.svn/**",
        "test/**",
        "**/CVS/**"
    ]

    private static final PathMatchingResourcePatternResolver RESOLVER = new PathMatchingResourcePatternResolver()

    /**
     * A default resolver used if none is specified to the resource resolving methods in this class.
     */
    Closure resourceResolver = { pattern ->
        try {
            return RESOLVER.getResources(pattern)
        }
        catch(Throwable e) {
            return [] as Resource[]
        }
    }

    BuildSettings buildSettings
    GriffonPluginManager pluginManager
    private String pluginDirPath
    private Map cache = new ConcurrentHashMap()
    private Map pluginToDirNameMap = new ConcurrentHashMap()
    private Map pluginMetaDataMap = new ConcurrentHashMap()
    private Map<String, PluginInfo> pluginInfosMap = new ConcurrentHashMap<String, PluginInfo>()
    private Map<String, PluginInfo> pluginInfoToSourceMap = new ConcurrentHashMap<String, PluginInfo>()
    private pluginLocations

    PluginBuildSettings(BuildSettings buildSettings) {
        this(buildSettings, null)
    }

    PluginBuildSettings(BuildSettings buildSettings, GriffonPluginManager pluginManager) {
        // We use null-safe navigation on buildSettings because otherwise
        // lots of unit tests will fail.
        this.buildSettings = buildSettings
        this.pluginManager = pluginManager
        this.pluginDirPath = buildSettings?.projectPluginsDir?.absolutePath
        this.pluginLocations = buildSettings?.config?.griffon?.plugin?.location
    }

    /**
     * Clears any cached entries.
     */
    void clearCache() {
        cache.clear()
        pluginToDirNameMap.clear()
        pluginMetaDataMap.clear()
        pluginInfosMap.clear()
        pluginInfoToSourceMap.clear()
    }

    /**
     * Returns an array of PluginInfo objects
     */
    GriffonPluginInfo[] getPluginInfos(String pluginDirPath=this.pluginDirPath) {
        if (pluginInfosMap) {
            return cache['pluginInfoList']
        }
        def pluginInfos = []
        Resource[] pluginDescriptors = getPluginDescriptors()
        def pluginDescriptorReader = new CompositePluginDescriptorReader(this)
        for (desc in pluginDescriptors) {
            try {
                GriffonPluginInfo info = pluginDescriptorReader.readPluginInfo(desc)
                if (info != null) {
                    pluginInfos << info
                    pluginInfosMap.put(info.name, info)
                    pluginInfosMap.put(info.fullName, info)
                }
            }
            catch (e) {
                // ignore, not a valid plugin directory
            }
        }
        cache['pluginInfoList'] = pluginInfos as GriffonPluginInfo[]
        return pluginInfos as GriffonPluginInfo[]
    }

    /**
     * Returns true if the specified plugin location is an inline location.
     */
    boolean isInlinePluginLocation(Resource pluginLocation) {
        getPluginDirectories() // initialize the cache
        return cache['inlinePluginLocations']?.contains(pluginLocation)
    }

    /**
     * Returns an array of the inplace plugin locations.
     */
    Resource[] getInlinePluginDirectories() {
        getPluginDirectories() // initailize the cache
        def locations = cache['inlinePluginLocations'] ?: []
        return locations as Resource[]
    }

    /**
     * Obtains a PluginInfo for the installed plugin directory.
     */
    GriffonPluginInfo getPluginInfo(String pluginBaseDir) {
        if (!pluginInfosMap) getPluginInfos() // initialize the infos
        def dir = new FileSystemResource(pluginBaseDir)
        def descLocation = getDescriptorForPlugin(dir)
        if (descLocation) {
            def pluginName = GriffonUtil.getPluginName(descLocation.filename)
            return pluginInfosMap[pluginName]
        }
    }

    /**
     * Obtains a PluginInfo for the installed plugin directory.
     */
    GriffonPluginInfo getPluginInfoForName(String pluginName) {
        if (!pluginInfosMap) getPluginInfos() // initialize the infos
        return pluginInfosMap[pluginName]
    }

    /**
     * Gets a PluginInfo for a particular source file if its contained within that plugin
     */
    GriffonPluginInfo getPluginInfoForSource(String sourceFile) {
        if (pluginInfoToSourceMap[sourceFile]) {
            return pluginInfoToSourceMap[sourceFile]
        }

        def pluginDirs = getPluginDirectories()
        if (!pluginDirs) {
            return null
        }

        for (Resource pluginDir in pluginDirs) {
            def pluginPath = pluginDir.file.canonicalPath
            def sourcePath = new File(sourceFile).canonicalPath
            if (sourcePath.startsWith(pluginPath)) {
                // Check the path of the source file relative to the
                // plugin directory. If the source file is in the
                // plugin's "test" directory, we ignore it. It's a
                // bit of a HACK, but not much else we can do without
                // a refactor of the plugin management.
                sourcePath = sourcePath.substring(pluginPath.length() + 1)
                if (!sourcePath.startsWith("test" + File.separator)) {
                    GriffonPluginInfo info = getPluginInfo(pluginPath)
                    if (info) {
                        pluginInfoToSourceMap[sourceFile] = info
                    }
                    return info
                }
            }
        }
    }

    /**
     * Obtains a Resource array of the Plugin metadata XML files used to describe the plugins provided resources
     */
    Resource[] getPluginXmlMetadata() {
        resolveResources 'allPluginXmlMetadata', false, { pluginDir ->
            resourceResolver("file:${pluginDir}/plugin.xml")
        }
    }

    /**
     * Returns XML about the plugin.
     */
    GPathResult getMetadataForPlugin(String pluginName) {
        if (pluginMetaDataMap[pluginName]) return pluginMetaDataMap[pluginName]

        Resource pluginDir = getPluginDirForName(pluginName)
        GPathResult result = getMetadataForPlugin(pluginDir)
        pluginMetaDataMap[pluginName] = result
        return result
    }

    /**
     * Returns XML metadata for the plugin.
     */
    GPathResult getMetadataForPlugin(Resource pluginDir) {
        try {
            return new XmlSlurper().parse(new File("$pluginDir.file.absolutePath/plugin.xml"))
        } catch (e) {
            // ignore
        }
        null
    }

    /**
     * Obtains an array of all Gant scripts that are availabe for execution in a Griffon application.
     */
    Resource[] getAvailableScripts() {
        def availableScripts = cache['availableScripts']
        if (!availableScripts) {

            def scripts = []
            def userHome = System.getProperty("user.home")
            def griffonHome = buildSettings.griffonHome.absolutePath
            def basedir = buildSettings.baseDir.absolutePath
            resourceResolver("file:${griffonHome}/scripts/**.groovy").each { if (!it.file.name.startsWith('_')) scripts << it }
            resourceResolver("file:${basedir}/scripts/*.groovy").each { if (!it.file.name.startsWith('_')) scripts << it }
            pluginScripts.each { if (!it.file.name.startsWith('_')) scripts << it }
            resourceResolver("file:${userHome}/.griffon/scripts/*.groovy").each { if (!it.file.name.startsWith('_')) scripts << it }
            availableScripts = scripts as Resource[]
            cache['availableScripts'] = availableScripts
        }
        return availableScripts
    }

    /**
     * Obtains an array of plugin provided Gant scripts available to a Griffon application.
     */
    Resource[] getPluginScripts() {
        resolveResources 'pluginScripts', false, { pluginDir ->
            resourceResolver("file:${pluginDir}/scripts/*.groovy")
        }
    }

    /**
     * Obtains an array of all plugin provided resource bundles.
     */
    Resource[] getPluginResourceBundles() {
        resolveResources 'pluginResourceBundles', false, { pluginDir ->
            resourceResolver("file:${pluginDir}/griffon-app/i18n/**/*.properties")
        }
    }

    /**
     * Obtains an array of all plugin provided source files (Java and Groovy).
     */
    Resource[] getPluginSourceFiles() {
        def sourceFiles = cache['sourceFiles']
        if (!sourceFiles) {
            cache['sourceFilesPerPlugin'] = [:]
            sourceFiles = new Resource[0]
            sourceFiles = resolvePluginResourcesAndAdd(sourceFiles, true) { pluginDir ->
                Resource[] pluginSourceFiles = resourceResolver("file:${pluginDir}/griffon-app/*")
                pluginSourceFiles = ArrayUtils.addAll(pluginSourceFiles,resourceResolver("file:${pluginDir}/src/java"))
                pluginSourceFiles = ArrayUtils.addAll(pluginSourceFiles,resourceResolver("file:${pluginDir}/src/groovy"))
                cache['sourceFilesPerPlugin'][pluginDir] = pluginSourceFiles
                return pluginSourceFiles
            }
            cache['sourceFiles'] = sourceFiles
        }
        return sourceFiles
    }

    Resource[] getPluginSourceFiles(File pluginDir) {
        getPluginSourceFiles() // initialize cache

        cache['sourceFilesPerPlugin'][pluginDir.absolutePath]
    }

    /**
     * Obtains an array of all plugin provided JAR files
     */
    Resource[] getPluginJarFiles() {
        resolveResources 'jarFiles', false, { pluginDir ->
            resourceResolver("file:${pluginDir}/lib/*.jar")
        }
    }

    /**
     * Obtains an array of all plugin provided Test JAR files
     */
    Resource[] getPluginTestFiles() {
        resolveResources 'testFiles', false, { pluginDir ->
            resourceResolver("file:${pluginDir}/dist/*-test.jar")
        }
    }

    /**
     * Obtains an array of all plugin provided JAR files for plugins that don't define
     * a dependencies.groovy.
     */
    Resource[] getUnmanagedPluginJarFiles() {
        resolveResources 'unmanagedPluginJars', false, { pluginDir ->
            if (!new File("${pluginDir}/dependencies.groovy").exists()) {
                return resourceResolver("file:${pluginDir}/lib/*.jar")
            }
        }
    }

    /**
     * Obtains a list of plugin directories for the application
     */
    Resource[] getPluginDirectories() {
        def pluginDirectoryResources = cache['pluginDirectoryResources']
        if (!pluginDirectoryResources) {
            cache['inlinePluginLocations'] = []
            def dirList = getImplicitPluginDirectories()

            // Also add any explicit plugin locations specified by the
            // BuildConfig setting "griffon.plugin.location.<name>"
            def pluginLocations = buildSettings?.config?.griffon?.plugin?.location?.findAll { it.value }
            if (pluginLocations) {
                dirList.addAll(pluginLocations.collect { key, value ->
                    FileSystemResource resource = new FileSystemResource(value)
                    cache['inlinePluginLocations'] << resource
                    return resource
                })
            }

            pluginDirectoryResources = dirList as Resource[]
            cache['pluginDirectoryResources'] = pluginDirectoryResources
        }
        return pluginDirectoryResources
    }

    /**
     * Loads a plugin descriptor using a GroovyClassLoader to void compiling
     * the codebase.
     */
    def loadPlugin(Resource pluginDescriptor) {
        loadPlugin(pluginDescriptor.file)
    }

    /**
     * Loads a plugin descriptor using a GroovyClassLoader to void compiling
     * the codebase.
     */
    def loadPlugin(File pluginFile) {
        def gcl = new GroovyClassLoader()
        return gcl.parseClass(pluginFile).newInstance()
    }

    /**
     * Obtains a list of plugin directories for the application sorted by
     * their dependency on other plugins
     */
    Resource[] getSortedPluginDirectories() {
        def sortedPluginDirectoryResources = cache['sortedPluginDirectoryResources']
        if (!sortedPluginDirectoryResources) {
            Resource[] pluginDirs = getPluginDirectories()    
            Map nodeps = [:]
            Map withdeps = [:]
            List sorted = []
            pluginDirs.each { r ->
                def info = getPluginInfo(r.file.absolutePath)
                def plug = loadPlugin(getPluginDescriptor(r))
                MetaClass mc = plug.metaClass
                if(mc.hasProperty(plug, 'dependsOn')) {
                    if(plug.dependsOn) {
                        withdeps[info.name] = [deps: plug.dependsOn, dir: r]
                    } else {
                        nodeps[info.name] = r
                        sorted << [name: info.name, dir: r]
                    }
                } else {
                    nodeps[info.name] = r
                    sorted << [name: info.name, dir: r]
                }
            }
    
            def resolveDeps
            resolveDeps = { name, values ->
                if(sorted.find{ it.name == name }) return
                values.deps.each { n, v ->
                    if(withdeps[n]) resolveDeps(n, withdeps[n])
                }
                def index = -1
                values.deps.each { n, v ->
                   index = Math.max(index, sorted.indexOf(sorted.find{it.name == n}))  
                }
                sorted = insert(sorted, [name: name, dir: values.dir], index + 1)
            }

            withdeps.each(resolveDeps)
            sortedPluginDirectoryResources = sorted.collect([]){ it.dir } as Resource[]
            cache['sortedPluginDirectoryResources'] = sortedPluginDirectoryResources
        }
        return sortedPluginDirectoryResources
    }

    private static List insert(List list, obj, int index) {
        int size = list.size()
        if(index >= size) {
            list[index] = obj
            return list
        } else {
            def head = list[0..<index]
            def tail = list[index..-1]
            return head + [obj] + tail 
        }
    }

    /**
     * Returns only the PluginInfo objects that support the current Environment and BuildScope.
     *
     * @see griffon.util.Environment
     * @see griffon.util.BuildScope
     */
    GriffonPluginInfo[] getSupportedPluginInfos() {
        if (pluginManager == null) return getPluginInfos()

        def pluginInfos = getPluginInfos().findAll {GriffonPluginInfo info ->
            GriffonPlugin plugin = pluginManager.getGriffonPlugin(info.getName())
            return plugin?.supportsCurrentScopeAndEnvironment()
        }
        return pluginInfos as GriffonPluginInfo[]
    }

    /**
     * Returns a list of all plugin directories in both the given path
     * and the global "plugins" directory together.
     */
    List<Resource> getImplicitPluginDirectories() {
        def dirList = []
        def directoryNamePredicate = {
            it.isDirectory() && (!it.name.startsWith(".") && it.name.indexOf('-') >- 1)
        }

        for (pluginBase in getPluginBaseDirectories()) {
            List pluginDirs = new File(pluginBase).listFiles().findAll(directoryNamePredicate).collect { new FileSystemResource(it) }
            dirList.addAll pluginDirs
        }

        return dirList
    }

    /**
     * Gets a list of all the known plugin base directories (directories where plugins are installed to).
     */
    List<String> getPluginBaseDirectories() {
        def list = []
        if (pluginDirPath) list << pluginDirPath
        String globalPluginPath = buildSettings?.globalPluginsDir?.path
        if (globalPluginPath) list << globalPluginPath
        return list
    }

    /**
     * Returns true if the specified plugin directory is a global plugin.
     */
    boolean isGlobalPluginLocation(Resource pluginDir) {
        def globalPluginsDir = buildSettings?.globalPluginsDir?.canonicalFile
        def containingDir = pluginDir?.file?.parentFile?.canonicalFile
        if (globalPluginsDir || containingDir) {
            return globalPluginsDir == containingDir
        }
        return false
    }

    /**
     * Obtains a reference to all artefact resources (all Groovy files contained within the
     * griffon-app directory of plugins or applications).
     */
    Resource[] getArtefactResources() {
        def basedir = buildSettings.baseDir.absolutePath
        def allArtefactResources = cache['allArtefactResources']
        if (!allArtefactResources) {
            def resources = [] as Resource[]

            // first scan plugin sources. These need to be loaded first
            resources = resolvePluginResourcesAndAdd(resources, true) { String pluginDir ->
                getArtefactResourcesForOne(pluginDir)
            }

            // now build of application resources so that these can override plugin resources
            resources = ArrayUtils.addAll(resources, getArtefactResourcesForOne(new File(basedir).canonicalFile.absolutePath))

            allArtefactResources = resources
            cache['allArtefactResources'] = resources
        }
        return allArtefactResources
    }

    /**
     * Returns an array of all artefacts in the given application or
     * plugin directory as Spring resources.
     */
    Resource[] getArtefactResourcesForOne(String projectDir) {
        return resourceResolver("file:${projectDir}/griffon-app/**/*.groovy")
    }

    /**
     * Obtains an array of all plugin descriptors (the root classes that end with *GriffonPlugin.groovy).
     */
    Resource[] getPluginDescriptors() {
        def pluginDescriptors = cache['pluginDescriptors']
        if (!pluginDescriptors) {
            def pluginDirs = getPluginDirectories().toList()
            if (buildSettings?.baseDir) {
                pluginDirs << new FileSystemResource(buildSettings.baseDir)
            }
            def descriptors = []
            for (Resource dir in pluginDirs) {
                def desc = getPluginDescriptor(dir)
                if (desc) {
                    descriptors << desc
                }
            }

            pluginDescriptors = descriptors as Resource[]
            cache['pluginDescriptors'] = pluginDescriptors
        }
        return pluginDescriptors
    }

    /**
     * Returns the plugin descriptor for the Given plugin directory.
     *
     * @param pluginDir The plugin directory
     * @return The plugin descriptor
     */
    Resource getPluginDescriptor(Resource pluginDir) {
        File f = pluginDir?.file.listFiles()?.find { it.name.endsWith("GriffonPlugin.groovy") }
        if (f) return new FileSystemResource(f)
    }

    /**
     * Obtains an array of all plugin lib directories.
     */
    Resource[] getPluginLibDirectories() {
        resolveResources 'pluginLibs', false, { pluginDir ->
            resourceResolver("file:${pluginDir}/lib")
        }
    }

    /**
     * Obtains an array of all plugin i18n directories.
     */
    Resource[] getPluginI18nDirectories() {
        resolveResources 'plugin18nDirectories', false, { pluginDir ->
            resourceResolver("file:${pluginDir}/griffon-app/i18n")
        }
    }

    /**
     * Obtains the path to the global plugins directory.
     */
    String getGlobalPluginsPath() { buildSettings?.globalPluginsDir?.path }

    /**
     * Obtains a plugin directory for the given name.
     */
    Resource getPluginDirForName(String pluginName) {
        Resource pluginResource = pluginToDirNameMap[pluginName]
        if (!pluginResource) {
            try {
                GriffonPluginInfo pluginInfo = getPluginInfoForName(pluginName)
                File pluginFile = pluginInfo?.pluginDir?.file

                // If the plugin can't be found in one of the standard
                // locations, check whether it's an in-place plugin.

                if (!pluginFile && pluginLocations) {
                    def pluginLoc = pluginLocations.find { key, value -> pluginName == key }
                    // maybe the plugin name includes a version suffix so attempt startsWith
                    if (!pluginLoc) {
                        pluginLoc = pluginLocations.find { key, value -> pluginName.startsWith(key) }
                    }
                    if (pluginLoc?.value) pluginFile = new File(pluginLoc.value.toString())
                }

                pluginResource = pluginFile ? new FileSystemResource(pluginFile) : null
                if (pluginResource) {
                    pluginToDirNameMap[pluginName] = pluginResource
                }
            }
            catch (IOException ignore) {
                pluginResource = null
            }
        }
        return pluginResource
    }

    /**
     * Obtains the 'base' plugin descriptor, which is the plugin descriptor of the current plugin project.
     */
    Resource getBasePluginDescriptor() {
        def basePluginDescriptor = cache['basePluginDescriptor']
        if (!basePluginDescriptor) {
            basePluginDescriptor = getDescriptorForPlugin(
                    new FileSystemResource(buildSettings.baseDir.absolutePath))
            if (basePluginDescriptor) {
                cache['basePluginDescriptor'] = basePluginDescriptor
            }
        }
        return basePluginDescriptor
    }

    /**
     * Returns the descriptor location for the given plugin directory. The descriptor is the Groovy
     * file that ends with *GriffonPlugin.groovy
     */
    Resource getDescriptorForPlugin(Resource pluginDir) {
        FileSystemResource descriptor = null
        File baseFile = pluginDir.file.canonicalFile
        File basePluginFile = baseFile.listFiles().find { it.name.endsWith("GriffonPlugin.groovy")}

        if (basePluginFile?.exists()) {
            descriptor = new FileSystemResource(basePluginFile)
        }
        return descriptor
    }

    void initBinding(GantBinding binding) {
        final PluginBuildSettings self = this
        binding.setVariable('getPluginDirForName') { String pluginName ->
            self.getPluginDirForName(pluginName)
        }
    }

    private Resource[] resolveResources(String key, boolean processExcludes, Closure c) {
        def resources = cache[key]
        if (!resources) {
            resources = new Resource[0]
            resources = resolvePluginResourcesAndAdd(resources, processExcludes, c)
            cache[key] = resources
        }
        return resources
    }

    /**
     * Takes a Resource[] and optional pluginsDirPath and goes through each plugin directory.
     * It will then used the provided resolving resolving closures to attempt to resolve a new
     * set of resources to add to the original passed array.
     *
     * A new array is then returned that contains any additiona plugin resources that were
     * resolved by the expression passed in the closure.
     */
    private resolvePluginResourcesAndAdd(Resource[] originalResources, boolean processExcludes, Closure resolver) {
        Resource[] pluginDirs = getPluginDirectories()
        for (dir in pluginDirs) {
            def newResources = dir ? resolver(dir.file.absolutePath) : null
            if (newResources) {
                if (processExcludes) {
                    def excludes = EXCLUDED_RESOURCES
                    newResources = newResources.findAll { Resource r ->
                        !excludes.any { r.file.absolutePath.endsWith(it) }
                    }
                }
                originalResources = ArrayUtils.addAll(originalResources, newResources as Resource[])
            }
        }
        return originalResources
    }
}
