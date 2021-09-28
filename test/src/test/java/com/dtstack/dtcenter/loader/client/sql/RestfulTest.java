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

package com.dtstack.dtcenter.loader.client.sql;

import com.dtstack.dtcenter.loader.client.BaseTest;
import com.dtstack.dtcenter.loader.client.ClientCache;
import com.dtstack.dtcenter.loader.client.IClient;
import com.dtstack.dtcenter.loader.dto.source.RestfulSourceDTO;
import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import com.dtstack.dtcenter.loader.source.DataSourceType;
import org.junit.Test;

/**
 * restful 测试类
 *
 * @author ：wangchuan
 * date：Created in 上午10:33 2021/6/7
 * company: www.dtstack.com
 */
public class RestfulTest extends BaseTest {

    // 构建client
    private static final IClient CLIENT = ClientCache.getClient(DataSourceType.RESTFUL.getVal());

    // 构建数据源信息
    private static final RestfulSourceDTO SOURCE_DTO = RestfulSourceDTO.builder()
            .url("https://www.baidu.com/")
            .build();

    /**
     * 连通性测试
     */
    @Test
    public void testCon() {
        Boolean isConnected = CLIENT.testCon(SOURCE_DTO);
        if (Boolean.FALSE.equals(isConnected)) {
            throw new DtLoaderException("connection exception");
        }
    }
}
