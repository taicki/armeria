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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.catalina.Service;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import com.linecorp.armeria.server.AbstractServerTest;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.logging.LoggingService;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.concurrent.Future;

public class TomcatServiceTest extends AbstractServerTest {

    private static final Pattern CR_OR_LF = Pattern.compile("[\\r\\n]");

    private static final String SERVICE_NAME = "TomcatServiceTest";

    private final List<Service> tomcatServices = new ArrayList<>();

    @Override
    protected void configureServer(ServerBuilder sb) {
        sb.serviceUnder(
                "/tc/",
                TomcatServiceBuilder.forCurrentClassPath("tomcat_service")
                                    .serviceName(SERVICE_NAME)
                                    .configurator(s -> Collections.addAll(tomcatServices, s.findServices()))
                                    .build()
                                    .decorate(LoggingService::new));

        sb.serviceUnder(
                "/tc_jar/",
                TomcatService.forClassPath(Future.class).decorate(LoggingService::new));

        sb.serviceUnder(
                "/tc_jar_altroot/",
                TomcatService.forClassPath(Future.class, "/io/netty/util/concurrent")
                             .decorate(LoggingService::new));
    }

    @Test
    public void testJsp() throws Exception {
        try (CloseableHttpClient hc = HttpClients.createMinimal()) {
            try (CloseableHttpResponse res = hc.execute(new HttpGet(uri("/tc/")))) {
                assertThat(res.getStatusLine().toString(), is("HTTP/1.1 200 OK"));
                assertThat(res.getFirstHeader(HttpHeaderNames.CONTENT_TYPE.toString()).getValue(),
                           startsWith("text/html"));
                final String actualContent = CR_OR_LF.matcher(EntityUtils.toString(res.getEntity()))
                                                     .replaceAll("");
                assertThat(actualContent, is(
                        "<html><body>" +
                        "<p>Hello, Armerian World!</p>" +
                        "<p>Have you heard about the class 'io.netty.buffer.ByteBuf'?</p>" +
                        "<p>Context path: </p>" + // ROOT context path
                        "<p>Request URI: /</p>" +
                        "</body></html>"));
            }
        }
    }

    @Test
    public void testGetQueryString() throws Exception {
        try (CloseableHttpClient hc = HttpClients.createMinimal()) {
            try (CloseableHttpResponse res = hc.execute(
                    new HttpGet(uri("/tc/query_string.jsp?foo=%31&bar=%32")))) {

                assertThat(res.getStatusLine().toString(), is("HTTP/1.1 200 OK"));
                assertThat(res.getFirstHeader(HttpHeaderNames.CONTENT_TYPE.toString()).getValue(),
                           startsWith("text/html"));
                final String actualContent = CR_OR_LF.matcher(EntityUtils.toString(res.getEntity()))
                                                     .replaceAll("");
                assertThat(actualContent, is(
                        "<html><body>" +
                        "<p>foo is 1</p>" +
                        "<p>bar is 2</p>" +
                        "</body></html>"));
            }
        }
    }

    @Test
    public void testPostQueryString() throws Exception {
        try (CloseableHttpClient hc = HttpClients.createMinimal()) {
            final HttpPost post = new HttpPost(uri("/tc/query_string.jsp?foo=3"));
            post.setEntity(new UrlEncodedFormEntity(
                    Collections.singletonList(new BasicNameValuePair("bar", "4")), StandardCharsets.UTF_8));

            try (CloseableHttpResponse res = hc.execute(post)) {
                assertThat(res.getStatusLine().toString(), is("HTTP/1.1 200 OK"));
                assertThat(res.getFirstHeader(HttpHeaderNames.CONTENT_TYPE.toString()).getValue(),
                           startsWith("text/html"));
                final String actualContent = CR_OR_LF.matcher(EntityUtils.toString(res.getEntity()))
                                                     .replaceAll("");
                assertThat(actualContent, is(
                        "<html><body>" +
                        "<p>foo is 3</p>" +
                        "<p>bar is 4</p>" +
                        "</body></html>"));
            }
        }
    }

    @Test
    public void testAddressesAndPorts() throws Exception {
        try (CloseableHttpClient hc = HttpClients.createMinimal()) {
            try (CloseableHttpResponse res = hc.execute(new HttpGet(uri("/tc/addrs_and_ports.jsp")))) {
                assertThat(res.getStatusLine().toString(), is("HTTP/1.1 200 OK"));
                assertThat(res.getFirstHeader(HttpHeaderNames.CONTENT_TYPE.toString()).getValue(),
                           startsWith("text/html"));
                final String actualContent = CR_OR_LF.matcher(EntityUtils.toString(res.getEntity()))
                                                     .replaceAll("");

                assertTrue(actualContent, actualContent.matches(
                        "<html><body>" +
                        "<p>RemoteAddr: 127\\.0\\.0\\.1</p>" +
                        "<p>RemoteHost: 127\\.0\\.0\\.1</p>" +
                        "<p>RemotePort: [1-9][0-9]+</p>" +
                        "<p>LocalAddr: (?!null)[^<]+</p>" +
                        "<p>LocalName: localhost</p>" +
                        "<p>LocalPort: " + server().activePort().get().localAddress().getPort() + "</p>" +
                        "</body></html>"));
            }
        }
    }

    @Test
    public void testConfigurator() throws Exception {
        assertThat(tomcatServices, hasSize(1));
        assertThat(tomcatServices.get(0).getName(), is(SERVICE_NAME));
    }

    @Test
    public void testJarBasedWebApp() throws Exception {
        try (CloseableHttpClient hc = HttpClients.createMinimal()) {
            try (CloseableHttpResponse res = hc.execute(
                    new HttpGet(uri("/tc_jar/io/netty/util/concurrent/Future.class")))) {
                assertThat(res.getStatusLine().toString(), is("HTTP/1.1 200 OK"));
                assertThat(res.getFirstHeader(HttpHeaderNames.CONTENT_TYPE.toString()).getValue(),
                           startsWith("application/java"));
            }
        }
    }

    @Test
    public void testJarBasedWebAppWithAlternativeRoot() throws Exception {
        try (CloseableHttpClient hc = HttpClients.createMinimal()) {
            try (CloseableHttpResponse res = hc.execute(new HttpGet(uri("/tc_jar_altroot/Future.class")))) {
                assertThat(res.getStatusLine().toString(), is("HTTP/1.1 200 OK"));
                assertThat(res.getFirstHeader(HttpHeaderNames.CONTENT_TYPE.toString()).getValue(),
                           startsWith("application/java"));
            }
        }
    }
}
