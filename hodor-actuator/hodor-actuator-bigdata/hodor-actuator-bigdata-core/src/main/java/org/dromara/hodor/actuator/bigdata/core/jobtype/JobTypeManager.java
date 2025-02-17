/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.dromara.hodor.actuator.bigdata.core.jobtype;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dromara.hodor.actuator.api.core.JobLogger;
import org.dromara.hodor.actuator.api.utils.Props;
import org.dromara.hodor.actuator.api.utils.PropsUtils;
import org.dromara.hodor.actuator.bigdata.core.exception.JobExecutionException;
import org.dromara.hodor.actuator.bigdata.core.executor.CommonJobProperties;
import org.dromara.hodor.actuator.bigdata.core.executor.JavaProcessJob;
import org.dromara.hodor.actuator.bigdata.core.executor.Job;
import org.dromara.hodor.actuator.bigdata.core.executor.NoopJob;
import org.dromara.hodor.actuator.bigdata.core.executor.ProcessJob;
import org.dromara.hodor.actuator.bigdata.core.utils.Utils;

public class JobTypeManager {

    private static final Logger log = LogManager.getLogger(JobTypeManager.class);

    public static final String DEFAULT_JOBTYPEPLUGINDIR = "jobtypes";

    // need jars.to.include property, will be loaded with user property
    private static final String JOBTYPECONFFILE = "plugin.properties";

    // not exposed to users
    private static final String JOBTYPESYSCONFFILE = "private.properties";

    // common properties for multiple plugins
    private static final String COMMONCONFFILE = "common.properties";

    // common private properties for multiple plugins
    private static final String COMMONSYSCONFFILE = "commonprivate.properties";

    private final String jobTypePluginDir; // the dir for jobtype plugins

    private final ClassLoader parentLoader;

    private final Props globalProperties;

    private JobTypePluginSet pluginSet;

    public JobTypeManager(final String jobtypePluginDir, final Props globalProperties,
                          final ClassLoader parentClassLoader) {
        this.jobTypePluginDir = jobtypePluginDir;
        this.parentLoader = parentClassLoader;
        this.globalProperties = globalProperties;

        loadPlugins();
    }

    public void loadPlugins() throws JobTypeManagerException {
        final JobTypePluginSet plugins = new JobTypePluginSet();

        loadDefaultTypes(plugins);
        if (this.jobTypePluginDir != null) {
            final File pluginDir = new File(this.jobTypePluginDir);
            if (pluginDir.exists()) {
                log.info("Job type plugin directory set. Loading extra job types from "
                    + pluginDir);
                try {
                    loadPluginJobTypes(plugins);
                } catch (final Exception e) {
                    log.info("Plugin jobtypes failed to load. " + e.getCause(), e);
                    throw new JobTypeManagerException(e);
                }
            }
        }

        // Swap the plugin set. If exception is thrown, then plugin isn't swapped.
        synchronized (this) {
            this.pluginSet = plugins;
        }
    }

    private void loadDefaultTypes(final JobTypePluginSet plugins)
        throws JobTypeManagerException {
        log.info("Loading plugin default job types");
        plugins.addPluginClass("command", ProcessJob.class);
        plugins.addPluginClass("javaprocess", JavaProcessJob.class);
        plugins.addPluginClass("noop", NoopJob.class);
    }

    // load Job Types from jobtype plugin dir
    private void loadPluginJobTypes(final JobTypePluginSet plugins)
        throws JobTypeManagerException {
        final File jobPluginsDir = new File(this.jobTypePluginDir);

        if (!jobPluginsDir.exists()) {
            log.error("Job type plugin dir " + this.jobTypePluginDir
                + " doesn't exist. Will not load any external plugins.");
            return;
        } else if (!jobPluginsDir.isDirectory()) {
            throw new JobTypeManagerException("Job type plugin dir "
                + this.jobTypePluginDir + " is not a directory!");
        } else if (!jobPluginsDir.canRead()) {
            throw new JobTypeManagerException("Job type plugin dir "
                + this.jobTypePluginDir + " is not readable!");
        }

        // Load the common properties used by all jobs that are run
        Props commonPluginJobProps = null;
        final File commonJobPropsFile = new File(jobPluginsDir, COMMONCONFFILE);
        if (commonJobPropsFile.exists()) {
            log.info("Common plugin job props file " + commonJobPropsFile
                + " found. Attempt to load.");
            try {
                commonPluginJobProps = new Props(this.globalProperties, commonJobPropsFile);
            } catch (final IOException e) {
                throw new JobTypeManagerException(
                    "Failed to load common plugin job properties" + e.getCause());
            }
        } else {
            log.info("Common plugin job props file " + commonJobPropsFile
                + " not found. Using only globals props");
            commonPluginJobProps = new Props(this.globalProperties);
        }

        // Loads the common properties used by all plugins when loading
        Props commonPluginLoadProps = null;
        final File commonLoadPropsFile = new File(jobPluginsDir, COMMONSYSCONFFILE);
        if (commonLoadPropsFile.exists()) {
            log.info("Common plugin load props file " + commonLoadPropsFile
                + " found. Attempt to load.");
            try {
                commonPluginLoadProps = new Props(null, commonLoadPropsFile);
            } catch (final IOException e) {
                throw new JobTypeManagerException(
                    "Failed to load common plugin loader properties" + e.getCause());
            }
        } else {
            log.info("Common plugin load props file " + commonLoadPropsFile
                + " not found. Using empty props.");
            commonPluginLoadProps = new Props();
        }

        plugins.setCommonPluginJobProps(commonPluginJobProps);
        plugins.setCommonPluginLoadProps(commonPluginLoadProps);

        // Loading job types
        final File[] files = jobPluginsDir.listFiles();
        if (Objects.isNull(files)) {
            return;
        }
        for (final File dir : files) {
            if (dir.isDirectory() && dir.canRead()) {
                try {
                    loadJobTypes(dir, plugins);
                } catch (final Exception e) {
                    log.error(
                        "Failed to load jobtype " + dir.getName() + e.getMessage(), e);
                    throw new JobTypeManagerException(e);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadJobTypes(final File pluginDir, final JobTypePluginSet plugins)
        throws JobTypeManagerException {
        // Directory is the jobtypeName
        final String jobTypeName = pluginDir.getName();
        log.info("Loading plugin " + jobTypeName);

        Props pluginJobProps;
        Props pluginLoadProps = null;

        final File pluginJobPropsFile = new File(pluginDir, JOBTYPECONFFILE);
        final File pluginLoadPropsFile = new File(pluginDir, JOBTYPESYSCONFFILE);

        if (!pluginLoadPropsFile.exists()) {
            log.info("Plugin load props file " + pluginLoadPropsFile
                + " not found.");
            return;
        }

        try {
            final Props commonPluginJobProps = plugins.getCommonPluginJobProps();
            final Props commonPluginLoadProps = plugins.getCommonPluginLoadProps();
            if (pluginJobPropsFile.exists()) {
                pluginJobProps = new Props(commonPluginJobProps, pluginJobPropsFile);
            } else {
                pluginJobProps = new Props(commonPluginJobProps);
            }

            pluginLoadProps = new Props(commonPluginLoadProps, pluginLoadPropsFile);
            pluginLoadProps.put("plugin.dir", pluginDir.getAbsolutePath());
            pluginLoadProps = PropsUtils.resolveProps(pluginLoadProps);
        } catch (final Exception e) {
            log.error("pluginLoadProps to help with debugging: " + pluginLoadProps);
            throw new JobTypeManagerException("Failed to get jobtype properties"
                + e.getMessage(), e);
        }
        // Add properties into the plugin set
        plugins.addPluginLoadProps(jobTypeName, pluginLoadProps);
        plugins.addPluginJobProps(jobTypeName, pluginJobProps);

        final ClassLoader jobTypeLoader =
            loadJobTypeClassLoader(pluginDir, jobTypeName, plugins);
        final String jobtypeClass = pluginLoadProps.getString("jobtype.class");

        if (jobtypeClass == null) {
            log.error("jobtype.class not found in private.properties.");
            return;
        }

        Class<? extends Job> clazz;
        try {
            clazz = (Class<? extends Job>) jobTypeLoader.loadClass(jobtypeClass);
        } catch (final ClassNotFoundException e) {
            throw new JobTypeManagerException(e);
        }

        log.info("Verifying job plugin " + jobTypeName);
        try {
            final Props fakeSysProps = new Props(pluginLoadProps);
            final Props fakeJobProps = new Props(pluginJobProps);
            Utils.callConstructor(clazz, "dummy", fakeSysProps,
                fakeJobProps, log);
        } catch (final Throwable t) {
            log.info("Jobtype " + jobTypeName + " failed test!", t);
            throw new JobExecutionException(t);
        }

        // Checked pass to add plugin class
        plugins.addPluginClass(jobTypeName, clazz);
        log.info("Loaded jobtype " + jobTypeName + " " + jobtypeClass);
    }

    /**
     * Creates and loads all plugin resources (jars) into a ClassLoader
     */
    private ClassLoader loadJobTypeClassLoader(final File pluginDir,
                                               final String jobTypeName, final JobTypePluginSet plugins) {
        // sysconf says what jars/confs to load
        final List<URL> resources = new ArrayList<>();
        final Props pluginLoadProps = plugins.getPluginLoaderProps(jobTypeName);

        try {
            // first global classpath
            log.info("Adding global resources for " + jobTypeName);
            final List<String> typeGlobalClassPath =
                pluginLoadProps.getStringList("jobtype.global.classpath", null, ",");
            addJarResource(resources, typeGlobalClassPath);

            // type specific classpath
            log.info("Adding type resources.");
            final List<String> typeClassPath =
                pluginLoadProps.getStringList("jobtype.classpath", null, ",");
            addJarResource(resources, typeClassPath);
            final List<String> jobtypeLibDirs =
                pluginLoadProps.getStringList("jobtype.lib.dir", null, ",");
            if (jobtypeLibDirs != null) {
                for (final String libDir : jobtypeLibDirs) {
                    final File[] files = new File(libDir).listFiles();
                    if (Objects.isNull(files)) {
                        continue;
                    }
                    for (final File f : files) {
                        if (f.getName().endsWith(".jar")) {
                            resources.add(f.toURI().toURL());
                            log.info("adding to classpath " + f.toURI().toURL());
                        }
                    }
                }
            }

            log.info("Adding type override resources.");
            for (final File f : Objects.requireNonNull(pluginDir.listFiles())) {
                if (f.getName().endsWith(".jar")) {
                    resources.add(f.toURI().toURL());
                    log.info("adding to classpath " + f.toURI().toURL());
                }
            }

        } catch (final MalformedURLException e) {
            throw new JobTypeManagerException(e);
        }

        // each job type can have a different class loader
        log.info(String
            .format("Classpath for plugin[dir: %s, JobType: %s]: %s", pluginDir, jobTypeName,
                resources));
        return new URLClassLoader(resources.toArray(new URL[0]), this.parentLoader);
    }

    private void addJarResource(List<URL> resources, List<String> classPath) throws MalformedURLException {
        if (classPath != null) {
            for (final String jar : classPath) {
                final URL cpItem = new File(jar).toURI().toURL();
                if (!resources.contains(cpItem)) {
                    log.info("adding to classpath " + cpItem);
                    resources.add(cpItem);
                }
            }
        }
    }

    public Job buildJobExecutor(final String jobKey, Props jobProps, final JobLogger log)
        throws JobTypeManagerException {
        // This is final because during build phase, you should never need to swap
        // the pluginSet for safety reasons
        final JobTypePluginSet pluginSet = getJobTypePluginSet();

        Job job = null;
        try {
            final String jobType = jobProps.getString(CommonJobProperties.JOB_TYPE);
            if (jobType == null || jobType.length() == 0) {
                /* throw an exception when job name is null or empty */
                throw new JobExecutionException(String.format(
                    "The 'type' parameter for job[%s] is null or empty", jobProps));
            }

            log.info("Building " + jobType + " job executor. ");

            final Class<?> executorClass = pluginSet.getPluginClass(jobType);
            if (executorClass == null) {
                throw new JobExecutionException(String.format("Job type [%s] is unrecognized.", jobType));
            }

            Props pluginJobProps = pluginSet.getPluginJobProps(jobType);
            // For default jobtypes, even though they don't have pluginJobProps configured,
            // they still need to load properties from common.properties file if it's present
            // because common.properties file is global to all jobtypes.
            if (pluginJobProps == null) {
                pluginJobProps = pluginSet.getCommonPluginJobProps();
            }
            if (pluginJobProps != null) {
                for (final String k : pluginJobProps.getKeySet()) {
                    if (!jobProps.containsKey(k)) {
                        jobProps.put(k, pluginJobProps.get(k));
                    }
                }
            }
            jobProps = PropsUtils.resolveProps(jobProps);

            Props pluginLoadProps = pluginSet.getPluginLoaderProps(jobType);
            if (pluginLoadProps != null) {
                pluginLoadProps = PropsUtils.resolveProps(pluginLoadProps);
            } else {
                // pluginSet.getCommonPluginLoadProps() will return null if there is no plugins directory.
                // hence assigning default Props() if that's the case
                pluginLoadProps = pluginSet.getCommonPluginLoadProps();
                if (pluginLoadProps == null) {
                    pluginLoadProps = new Props();
                }
            }

            job =
                (Job) Utils.callConstructor(executorClass, jobKey, pluginLoadProps,
                    jobProps, log);
        } catch (final Exception e) {
            log.error("Failed to build job executor for job " + jobKey
                + e.getMessage());
            throw new JobTypeManagerException("Failed to build job executor for job "
                + jobKey, e);
        } catch (final Throwable t) {
            log.error(
                "Failed to build job executor for job " + jobKey + t.getMessage(), t);
            throw new JobTypeManagerException("Failed to build job executor for job "
                + jobKey, t);
        }

        return job;
    }

    /**
     * Public for test reasons. Will need to move tests to the same package
     */
    public synchronized JobTypePluginSet getJobTypePluginSet() {
        return this.pluginSet;
    }
}
