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

package org.dromara.hodor.actuator.bigdata.jobtype;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.dromara.hodor.actuator.api.utils.Props;
import org.dromara.hodor.actuator.bigdata.core.executor.ProcessJob;
import org.dromara.hodor.actuator.bigdata.jobtype.javautils.JobUtils;
import org.dromara.hodor.actuator.bigdata.security.commons.SecurityUtils;
import org.dromara.hodor.actuator.bigdata.core.utils.JSONUtils;

public class JavaJobRunnerMain {

    public static final String JOB_CLASS = "job.class";
    public static final String DEFAULT_RUN_METHOD = "run";
    public static final String DEFAULT_CANCEL_METHOD = "cancel";

    // This is the Job interface method to get the properties generated by the
    // job.
    public static final String GET_GENERATED_PROPERTIES_METHOD =
            "getJobGeneratedProperties";

    public static final String CANCEL_METHOD_PARAM = "method.cancel";
    public static final String RUN_METHOD_PARAM = "method.run";
    public static final String[] PROPS_CLASSES = new String[]{Props.class.getName()};

    private static final PatternLayout DEFAULT_LAYOUT = PatternLayout.createDefaultLayout();

    public final Logger _logger;

    public String _cancelMethod;
    public String _jobName;
    public Object _javaObject;
    private boolean _isFinished = false;

    public static void main(String[] args) throws Exception {
        @SuppressWarnings("unused")
        JavaJobRunnerMain wrapper = new JavaJobRunnerMain();
    }

    @SuppressWarnings("DefaultCharset")
    public JavaJobRunnerMain() throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                cancelJob();
            }
        });

        try {
            _jobName = System.getenv(ProcessJob.JOB_NAME_ENV);
            String propsFile = System.getenv(ProcessJob.JOB_PROP_ENV);

            _logger = JobUtils.initJobLogger();

            Properties props = new Properties();
            props.load(new BufferedReader(new FileReader(propsFile)));

            _logger.info("Running job " + _jobName);
            String className = props.getProperty(JOB_CLASS);
            if (className == null) {
                throw new Exception("Class name is not set.");
            }
            _logger.info("Class name " + className);

            HadoopConfigurationInjector.injectResources(new Props(null, props));

            // Create the object using proxy
            if (SecurityUtils.shouldProxy(props)) {
                _javaObject = getObjectAsProxyUser(props, _logger, _jobName, className);
            } else {
                _javaObject = getObject(_jobName, className, props, _logger);
            }
            if (_javaObject == null) {
                _logger.info("Could not create java object to run job: " + className);
                throw new Exception("Could not create running object");
            }

            _cancelMethod =
                    props.getProperty(CANCEL_METHOD_PARAM, DEFAULT_CANCEL_METHOD);

            final String runMethod =
                    props.getProperty(RUN_METHOD_PARAM, DEFAULT_RUN_METHOD);
            _logger.info("Invoking method " + runMethod);

            if (SecurityUtils.shouldProxy(props)) {
                _logger.info("Proxying enabled.");
                runMethodAsProxyUser(props, _javaObject, runMethod);
            } else {
                _logger.info("Proxy check failed, not proxying run.");
                runMethod(_javaObject, runMethod);
            }
            _isFinished = true;

            // Get the generated properties and store them to disk, to be read
            // by ProcessJob.
            try {
                final Method generatedPropertiesMethod =
                        _javaObject.getClass().getMethod(GET_GENERATED_PROPERTIES_METHOD,
                                new Class<?>[]{});
                Object outputGendProps =
                        generatedPropertiesMethod.invoke(_javaObject, new Object[]{});
                if (outputGendProps != null) {
                    final Method toPropertiesMethod =
                            outputGendProps.getClass().getMethod("toProperties",
                                    new Class<?>[]{});
                    Properties properties =
                            (Properties) toPropertiesMethod.invoke(outputGendProps,
                                    new Object[]{});

                    Props outputProps = new Props(null, properties);
                    outputGeneratedProperties(outputProps);
                } else {
                    outputGeneratedProperties(new Props());
                }

            } catch (NoSuchMethodException e) {
                _logger.info(String.format(
                        "Apparently there isn't a method[%s] on object[%s], "
                                + "using empty Props object instead.",
                        GET_GENERATED_PROPERTIES_METHOD, _javaObject));
                outputGeneratedProperties(new Props());
            }
        } catch (Exception e) {
            _isFinished = true;
            throw e;
        }
    }

    private void runMethodAsProxyUser(Properties props, final Object obj,
                                      final String runMethod) throws IOException, InterruptedException {
        UserGroupInformation ugi =
                SecurityUtils.getProxiedUser(props, _logger, new Configuration());
        _logger.info("user " + ugi + " authenticationMethod "
                + ugi.getAuthenticationMethod());
        _logger.info("user " + ugi + " hasKerberosCredentials "
                + ugi.hasKerberosCredentials());
        SecurityUtils.getProxiedUser(props, _logger, new Configuration()).doAs(
                new PrivilegedExceptionAction<Void>() {
                    @Override
                    public Void run() throws Exception {
                        runMethod(obj, runMethod);
                        return null;
                    }
                });
    }

    private void runMethod(Object obj, String runMethod)
            throws IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {
        obj.getClass().getMethod(runMethod, new Class<?>[]{}).invoke(obj);
    }

    @SuppressWarnings("DefaultCharset")
    private void outputGeneratedProperties(Props outputProperties) {

        if (outputProperties == null) {
            _logger.info("  no gend props");
            return;
        }
        for (String key : outputProperties.getKeySet()) {
            _logger
                    .info("  gend prop " + key + " value:" + outputProperties.get(key));
        }

        String outputFileStr = System.getenv(ProcessJob.JOB_OUTPUT_PROP_FILE);
        if (outputFileStr == null) {
            return;
        }

        _logger.info("Outputting generated properties to " + outputFileStr);

        Map<String, String> properties = new LinkedHashMap<String, String>();
        for (String key : outputProperties.getKeySet()) {
            properties.put(key, outputProperties.getString(key));
        }

        Writer writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outputFileStr));
            JSONUtils.writePropsNoJarDependency(properties, writer);
        } catch (Exception e) {
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public void cancelJob() {
        if (_isFinished) {
            return;
        }
        _logger.info("Attempting to call cancel on this job");
        if (_javaObject == null) {
            return;
        }

        Method method = null;
        try {
            method = _javaObject.getClass().getMethod(_cancelMethod);
        } catch (SecurityException e) {
        } catch (NoSuchMethodException e) {
        }

        if (method != null) {
            try {
                method.invoke(_javaObject);
            } catch (Exception e) {
                if (_logger != null) {
                    _logger.error("Cancel method failed! ", e);
                }
            }
        } else {
            throw new RuntimeException("Job " + _jobName
                    + " does not have cancel method " + _cancelMethod);
        }
    }

    private static Object getObjectAsProxyUser(final Properties props,
                                               final Logger logger, final String jobName, final String className)
            throws Exception {
        Object obj =
                SecurityUtils.getProxiedUser(props, logger, new Configuration()).doAs(
                        new PrivilegedExceptionAction<Object>() {
                            @Override
                            public Object run() throws Exception {
                                return getObject(jobName, className, props, logger);
                            }
                        });

        return obj;
    }

    private static Object getObject(String jobName, String className,
                                    Properties properties, Logger logger) throws Exception {

        Class<?> runningClass =
                JavaJobRunnerMain.class.getClassLoader().loadClass(className);

        if (runningClass == null) {
            throw new Exception("Class " + className
                    + " was not found. Cannot run job.");
        }

        Class<?> propsClass = null;
        for (String propClassName : PROPS_CLASSES) {
            try {
                propsClass =
                        JavaJobRunnerMain.class.getClassLoader().loadClass(propClassName);
            } catch (ClassNotFoundException e) {
            }

            if (propsClass != null
                    && getConstructor(runningClass, String.class, propsClass) != null) {
                // is this the props class
                break;
            }
            propsClass = null;
        }

        Object obj = null;
        if (propsClass != null
                && getConstructor(runningClass, String.class, propsClass) != null) {
            // Create props class
            Constructor<?> propsCon =
                    getConstructor(propsClass, propsClass, Properties[].class);
            Object props =
                    propsCon.newInstance(null, new Properties[]{properties});

            Constructor<?> con =
                    getConstructor(runningClass, String.class, propsClass);
            logger.info("Constructor found " + con.toGenericString());
            obj = con.newInstance(jobName, props);
        } else if (getConstructor(runningClass, String.class, Properties.class) != null) {
            Constructor<?> con =
                    getConstructor(runningClass, String.class, Properties.class);
            logger.info("Constructor found " + con.toGenericString());
            obj = con.newInstance(jobName, properties);
        } else if (getConstructor(runningClass, String.class, Map.class) != null) {
            Constructor<?> con =
                    getConstructor(runningClass, String.class, Map.class);
            logger.info("Constructor found " + con.toGenericString());

            HashMap<Object, Object> map = new HashMap<Object, Object>();
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }
            obj = con.newInstance(jobName, map);
        } else if (getConstructor(runningClass, String.class) != null) {
            Constructor<?> con = getConstructor(runningClass, String.class);
            logger.info("Constructor found " + con.toGenericString());
            obj = con.newInstance(jobName);
        } else if (getConstructor(runningClass) != null) {
            Constructor<?> con = getConstructor(runningClass);
            logger.info("Constructor found " + con.toGenericString());
            obj = con.newInstance();
        } else {
            logger.error("Constructor not found. Listing available Constructors.");
            for (Constructor<?> c : runningClass.getConstructors()) {
                logger.info(c.toGenericString());
            }
        }
        return obj;
    }

    private static Constructor<?> getConstructor(Class<?> c, Class<?>... args) {
        try {
            Constructor<?> cons = c.getConstructor(args);
            return cons;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
