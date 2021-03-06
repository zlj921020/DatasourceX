/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtstack.dtcenter.common.loader.restful.http;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.dtstack.dtcenter.common.loader.common.DtClassThreadFactory;
import com.dtstack.dtcenter.loader.dto.source.ISourceDTO;
import com.dtstack.dtcenter.loader.dto.source.RestfulSourceDTO;
import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.conn.NoopIOSessionStrategy;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;

@Slf4j
public class HttpClientFactory {

    /**
     * http IO ????????????????????????????????????????????????????????? ????????????
     */
    private static final Integer IO_THREAD_COUNT = 1;

    /**
     * HTTP?????????????????????????????????
     */
    private static final Integer HTTP_CONNECT_TIMEOUT = 90;

    /**
     * Socket ???????????????????????????
     */
    private static final Integer HTTP_SOCKET_TIMEOUT = 90;

    /**
     * ?????? HTTP ?????????????????????????????????
     */
    private static final Integer HTTP_CONNECTION_REQUEST_TIMEOUT = 90;

    public static HttpClient createHttpClientAndStart(ISourceDTO sourceDTO) {
        HttpClient httpClient = createHttpClient(sourceDTO);
        httpClient.start();
        return httpClient;
    }

    public static HttpClient createHttpClient(ISourceDTO sourceDTO) {
        RestfulSourceDTO restfulSourceDTO = (RestfulSourceDTO) sourceDTO;
        // ?????? ConnectingIOReactor
        ConnectingIOReactor ioReactor = initIOReactorConfig();

        // ?????? http???https
        Registry<SchemeIOSessionStrategy> sessionStrategyRegistry =
                RegistryBuilder.<SchemeIOSessionStrategy>create()
                        .register("http", NoopIOSessionStrategy.INSTANCE)
                        .register("https", SSLIOSessionStrategy.getDefaultStrategy())
                        .build();
        // ?????????????????????
        PoolingNHttpClientConnectionManager cm = new PoolingNHttpClientConnectionManager(ioReactor, sessionStrategyRegistry);

        // ??????HttpAsyncClient
        CloseableHttpAsyncClient httpAsyncClient = createPoolingHttpClient(cm, restfulSourceDTO.getConnectTimeout(), restfulSourceDTO.getSocketTimeout());

        // ??????????????????
        ScheduledExecutorService clearConnService = initFixedCycleCloseConnection(cm);

        // ????????????HttpClientImpl
        return new HttpClient(restfulSourceDTO, httpAsyncClient, clearConnService);
    }


    /**
     * ????????? http ????????????
     *
     * @param connectTimeout ??????????????????
     * @param socketTimeout  socket ????????????
     * @return http ????????????
     */
    private static RequestConfig initRequestConfig(Integer connectTimeout, Integer socketTimeout) {
        final RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
        // ConnectTimeout:????????????.?????????????????????????????????????????????.
        Integer connTimeout = Objects.isNull(connectTimeout) ? HTTP_CONNECT_TIMEOUT : connectTimeout;
        Integer sockTimeout = Objects.isNull(socketTimeout) ? HTTP_SOCKET_TIMEOUT : socketTimeout;
        requestConfigBuilder.setConnectTimeout(connTimeout * 1000);
        // SocketTimeout:Socket????????????.?????????????????????????????????????????????????????????.
        requestConfigBuilder.setSocketTimeout(sockTimeout * 1000);
        // ConnectionRequestTimeout:httpclient??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
        requestConfigBuilder.setConnectionRequestTimeout(HTTP_CONNECTION_REQUEST_TIMEOUT * 1000);
        return requestConfigBuilder.build();
    }

    /**
     * ??????????????????
     *
     * @return ConnectingIOReactor
     */
    private static ConnectingIOReactor initIOReactorConfig() {
        IOReactorConfig ioReactorConfig = IOReactorConfig.custom().setIoThreadCount(IO_THREAD_COUNT).build();
        ConnectingIOReactor ioReactor;
        try {
            ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);
            return ioReactor;
        } catch (IOReactorException e) {
            throw new DtLoaderException(e.getMessage(), e);
        }
    }

    /**
     * ???????????????????????????????????????????????????
     *
     * @param cm connection ?????????
     * @return ?????????????????????
     */
    private static ScheduledExecutorService initFixedCycleCloseConnection(final PoolingNHttpClientConnectionManager cm) {
        // ??????????????????????????????
        ScheduledExecutorService connectionGcService = Executors.newSingleThreadScheduledExecutor(new DtClassThreadFactory("Loader-close-connection"));
        connectionGcService.scheduleAtFixedRate(() -> {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Close idle connections, fixed cycle operation");
                }
                cm.closeIdleConnections(3, TimeUnit.MINUTES);
            } catch (Exception ex) {
                log.error("", ex);
            }
        }, 30, 30, TimeUnit.SECONDS);
        return connectionGcService;
    }

    /**
     * ???????????? http client
     *
     * @param cm             http connection ?????????
     * @param connectTimeout ??????????????????
     * @param socketTimeout  socket ????????????
     * @return ?????? http client
     */
    private static CloseableHttpAsyncClient createPoolingHttpClient(PoolingNHttpClientConnectionManager cm, Integer connectTimeout, Integer socketTimeout) {

        RequestConfig requestConfig = initRequestConfig(connectTimeout, socketTimeout);
        HttpAsyncClientBuilder httpAsyncClientBuilder = HttpAsyncClients.custom();

        // ?????????????????????
        httpAsyncClientBuilder.setConnectionManager(cm);

        // ??????RequestConfig
        if (requestConfig != null) {
            httpAsyncClientBuilder.setDefaultRequestConfig(requestConfig);
        }
        return httpAsyncClientBuilder.build();
    }
}
