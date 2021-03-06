/*
 * Copyright 2015 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.server.http.tomcat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.catalina.Realm;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.core.StandardService;

/**
 * {@link TomcatService} configuration.
 */
public class TomcatServiceConfig {

    private final String serviceName;
    private final String engineName;
    private final Path baseDir;
    private final Realm realm;
    private final String hostname;
    private final Path docBase;
    private final String jarRoot;
    private final List<Consumer<? super StandardServer>> configurators;

    TomcatServiceConfig(String serviceName, String engineName, Path baseDir, Realm realm,
                        String hostname, Path docBase, String jarRoot,
                        List<Consumer<? super StandardServer>> configurators) {

        this.engineName = engineName;
        this.serviceName = serviceName;
        this.baseDir = baseDir;
        this.realm = realm;
        this.hostname = hostname;
        this.docBase = docBase;
        this.jarRoot = jarRoot;
        this.configurators = configurators;
    }

    /**
     * Returns the name of the {@link StandardService} of an embedded Tomcat.
     */
    public String serviceName() {
        return serviceName;
    }

    /**
     * Returns the name of the {@link StandardEngine} of an embedded Tomcat.
     */
    public String engineName() {
        return engineName;
    }

    /**
     * Returns the base directory of an embedded Tomcat.
     */
    public Path baseDir() {
        return baseDir;
    }

    /**
     * Returns the {@link Realm} of an embedded Tomcat.
     */
    public Realm realm() {
        return realm;
    }

    /**
     * Returns the hostname of an embedded Tomcat.
     */
    public String hostname() {
        return hostname;
    }

    /**
     * Returns the document base directory of a web application.
     */
    public Path docBase() {
        return docBase;
    }

    /**
     * Returns the path to the root directory of a web application inside a JAR/WAR.
     *
     * @return {@link Optional#empty()} if {@link #docBase()} is not a JAR/WAR file
     */
    public Optional<String> jarRoot() {
        return Optional.ofNullable(jarRoot);
    }

    /**
     * Returns the {@link Consumer}s that performs additional configuration operations against
     * the Tomcat {@link StandardServer} created by a {@link TomcatService}.
     */
    public List<Consumer<? super StandardServer>> configurators() {
        return configurators;
    }

    @Override
    public String toString() {
        return toString(this, serviceName(), engineName(), baseDir(), realm(), hostname(),
                        docBase(), jarRoot().orElse(null));
    }

    static String toString(Object holder, String serviceName, String engineName,
                           Path baseDir, Realm realm, String hostname, Path docBase, String jarRoot) {

        return holder.getClass().getSimpleName() +
               "(serviceName: " + serviceName +
               ", engineName: " + engineName +
               ", baseDir: " + baseDir +
               ", realm: " + realm.getClass().getSimpleName() +
               ", hostname: " + hostname +
               ", docBase: " + docBase +
               (jarRoot != null ? ", jarRoot: " + jarRoot : "") +
               ')';
    }
}
