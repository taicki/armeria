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

package com.linecorp.armeria.server;

import static java.util.Objects.requireNonNull;

import java.net.IDN;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.netty.handler.ssl.SslContext;

/**
 * A <a href="https://en.wikipedia.org/wiki/Virtual_hosting#Name-based">name-based virtual host</a>.
 * A {@link VirtualHost} contains the following information:
 * <ul>
 *   <li>the hostname pattern, as defined in
 *       <a href="http://tools.ietf.org/html/rfc2818#section-3.1">the section 3.1 of RFC2818</a></li>
 *   <li>{@link SslContext} if TLS is enabled</li>
 *   <li>the list of available {@link Service}s and their {@link PathMapping}s</li>
 * </ul>
 *
 * @see VirtualHostBuilder
 */
public final class VirtualHost {

    /** Initialized later by {@link ServerConfig} via {@link #setServerConfig(ServerConfig)}. */
    private ServerConfig serverConfig;

    private final String hostnamePattern;
    private final SslContext sslContext;
    private final List<ServiceConfig> services;
    private final PathMappings<ServiceConfig> serviceMapping = new PathMappings<>();

    private String strVal;

    VirtualHost(String hostnamePattern, SslContext sslContext, Iterable<ServiceConfig> serviceConfigs) {

        this.hostnamePattern = normalizeHostnamePattern(hostnamePattern);
        this.sslContext = validateSslContext(sslContext);

        requireNonNull(serviceConfigs, "serviceConfigs");

        final List<ServiceConfig> servicesCopy = new ArrayList<>();

        for (ServiceConfig c : serviceConfigs) {
            c = c.build(this);
            servicesCopy.add(c);
            serviceMapping.add(c.pathMapping(), c);
        }

        services = Collections.unmodifiableList(servicesCopy);
        serviceMapping.freeze();
    }

    /**
     * IDNA ASCII conversion and case normalization
     */
    static String normalizeHostnamePattern(String hostnamePattern) {
        requireNonNull(hostnamePattern, "hostnamePattern");
        if (needsNormalization(hostnamePattern)) {
            hostnamePattern = IDN.toASCII(hostnamePattern, IDN.ALLOW_UNASSIGNED);
        }
        return hostnamePattern.toLowerCase(Locale.US);
    }

    private static boolean needsNormalization(String hostnamePattern) {
        final int length = hostnamePattern.length();
        for (int i = 0; i < length; i ++) {
            int c = hostnamePattern.charAt(i);
            if (c > 0x7F) {
                return true;
            }
        }
        return false;
    }

    static SslContext validateSslContext(SslContext sslContext) {
        if (sslContext != null && !sslContext.isServer()) {
            throw new IllegalArgumentException("sslContext: " + sslContext + " (expected: server context)");
        }
        return sslContext;
    }

    /**
     * Returns the {@link Server} where this {@link VirtualHost} belongs to.
     */
    public Server server() {
        if (serverConfig == null) {
            throw new IllegalStateException("server is not configured yet.");
        }
        return serverConfig.server();
    }

    void setServerConfig(ServerConfig serverConfig) {
        if (this.serverConfig != null) {
            throw new IllegalStateException("VirtualHost cannot be added to more than one Server.");
        }

        this.serverConfig = requireNonNull(serverConfig, "serverConfig");
    }

    /**
     * Returns the hostname pattern of this virtual host, as defined in
     * <a href="http://tools.ietf.org/html/rfc2818#section-3.1">the section 3.1 of RFC2818</a>
     */
    public String hostnamePattern() {
        return hostnamePattern;
    }

    /**
     * Returns the {@link SslContext} of this virtual host.
     */
    public SslContext sslContext() {
        return sslContext;
    }

    /**
     * Returns the information about the {@link Service}s bound to this virtual host.
     */
    public List<ServiceConfig> serviceConfigs() {
        return services;
    }

    /**
     * Finds the {@link Service} whose {@link PathMapping} matches the {@code path}.
     *
     * @return the {@link Service} wrapped by {@link PathMapped} if there's a match.
     *         {@link PathMapped#empty()} if there's no match.
     */
    public PathMapped<ServiceConfig> findServiceConfig(String path) {
        return serviceMapping.apply(path);
    }

    @Override
    public String toString() {
        String strVal = this.strVal;
        if (strVal == null) {
            this.strVal = strVal = toString(getClass(), hostnamePattern(), sslContext(), serviceConfigs());
        }

        return strVal;
    }

    static String toString(
            Class<?> type, String hostnamePattern, SslContext sslContext, List<?> services) {

        StringBuilder buf = new StringBuilder();
        if (type != null) {
            buf.append(type.getSimpleName());
        }

        buf.append('(');
        buf.append(hostnamePattern);
        buf.append(", ssl: ");
        buf.append(sslContext != null);
        buf.append(", services: ");
        buf.append(services);
        buf.append(')');

        return buf.toString();
    }
}
