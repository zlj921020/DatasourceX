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
import com.dtstack.dtcenter.loader.client.ITsdb;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.dtstack.dtcenter.loader.dto.source.OpenTSDBSourceDTO;
import com.dtstack.dtcenter.loader.dto.tsdb.Aggregator;
import com.dtstack.dtcenter.loader.dto.tsdb.QueryResult;
import com.dtstack.dtcenter.loader.dto.tsdb.SubQuery;
import com.dtstack.dtcenter.loader.dto.tsdb.Suggest;
import com.dtstack.dtcenter.loader.dto.tsdb.TsdbPoint;
import com.dtstack.dtcenter.loader.dto.tsdb.TsdbQuery;
import com.dtstack.dtcenter.loader.enums.Granularity;
import com.dtstack.dtcenter.loader.source.DataSourceType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * OpenTSDB ?????????
 *
 * @author ???wangchuan
 * date???Created in ??????10:33 2021/6/7
 * company: www.dtstack.com
 */
public class OpenTSDBSpecialTest extends BaseTest {

    // ?????? tsdb client
    private static final ITsdb TSDB_CLIENT = ClientCache.getTsdb(DataSourceType.OPENTSDB.getVal());

    // ??????client
    private static final IClient CLIENT = ClientCache.getClient(DataSourceType.OPENTSDB.getVal());

    // ?????????????????????
    private static final OpenTSDBSourceDTO SOURCE_DTO = OpenTSDBSourceDTO.builder()
            .url("http://172.16.23.15:4242")
            .build();

    @BeforeClass
    public static void setUp () {
        List<TsdbPoint> points = Lists.newArrayList();
        for (int i = 0; i < 10; i++) {
            TsdbPoint point = new TsdbPoint.TsdbPointBuilder("loader_test_1")
                    .tag("host", "loader_host" + i % 2)
                    .tag("name", "loader_name" + i % 2)
                    .timestamp(new Date().getTime() - i * 3 * 60 * 1000)
                    .value(RandomUtils.nextDouble(1.0, 10000.0)).build();
            points.add(point);
        }
        TSDB_CLIENT.putSync(SOURCE_DTO, points);
    }

    /**
     * ??????????????????
     */
    @Test
    public void putSync() {
        TsdbPoint point = new TsdbPoint.TsdbPointBuilder("loader_test_1")
                .tag("host", "loader")
                .timestamp(new Date())
                .value(500).build();
        TSDB_CLIENT.putSync(SOURCE_DTO, Collections.singleton(point));
    }

    /**
     * ????????????????????????
     */
    @Test
    public void putSyncMulti() {
        List<TsdbPoint> points = Lists.newArrayList();
        for (int i = 0; i < 10; i++) {
            TsdbPoint point = new TsdbPoint.TsdbPointBuilder("loader_test_1")
                    .tag("host", "loader_host" + i % 2)
                    .tag("name", "loader_name" + i % 2)
                    .timestamp(new Date().getTime() - i * 3 * 60 * 1000)
                    .value(RandomUtils.nextDouble(1.0, 10000.0)).build();
            points.add(point);
        }
        TSDB_CLIENT.putSync(SOURCE_DTO, points);
    }

    /**
     * ??????????????????????????????2.3.0 ???????????????????????? query ??? delete ??? true ??????????????????
     */
    @Test(expected = Exception.class)
    public void deleteData() {
        long nowTime = System.currentTimeMillis();
        TSDB_CLIENT.deleteData(SOURCE_DTO, "loader_test_1", nowTime - 60 * 60 * 1000, nowTime);
    }

    /**
     * ?????????????????????????????????2.3.0 ???????????????????????? query ??? delete ??? true ??????????????????
     */
    @Test(expected = Exception.class)
    public void deleteMeta() {
        TSDB_CLIENT.deleteMeta(SOURCE_DTO, "loader_test_1", Maps.newHashMap());
    }

    /**
     * ??????????????? metric
     */
    @Test
    public void suggestMetric() {
        List<String> suggest = TSDB_CLIENT.suggest(SOURCE_DTO, Suggest.Metrics, null, 2);
        Assert.assertTrue(CollectionUtils.isNotEmpty(suggest));
    }

    /**
     * ??????????????? tagK
     */
    @Test
    public void suggestTagK() {
        List<String> suggest = TSDB_CLIENT.suggest(SOURCE_DTO, Suggest.Tagk, null, 2);
        Assert.assertTrue(CollectionUtils.isNotEmpty(suggest));
    }

    /**
     * ??????????????? tagV
     */
    @Test
    public void suggestTagV() {
        List<String> suggest = TSDB_CLIENT.suggest(SOURCE_DTO, Suggest.Tagv, null, 2);
        Assert.assertTrue(CollectionUtils.isNotEmpty(suggest));
    }

    /**
     * ????????????????????????
     */
    @Test
    public void getVersionInfo() {
        Map<String, String> versionInfo = TSDB_CLIENT.getVersionInfo(SOURCE_DTO);
        Assert.assertTrue(MapUtils.isNotEmpty(versionInfo));
    }

    /**
     * ????????????
     */
    @Test
    public void version() {
        String version = TSDB_CLIENT.version(SOURCE_DTO);
        Assert.assertTrue(StringUtils.isNotBlank(version));
    }

    /**
     * ????????????
     */
    @Test
    public void query() {
        long timeNow = System.currentTimeMillis();
        SubQuery subQuery = SubQuery.builder()
                .metric("loader_test_1")
                .aggregator(Aggregator.SUM.getName())
                .granularity(Granularity.M15.getName())
                .limit(5).build();
        TsdbQuery query = TsdbQuery.builder()
                .start(timeNow - 2 * 60 * 60 * 1000)
                .end(timeNow)
                .showType(true)
                .queries(Collections.singletonList(subQuery)).build();
        List<QueryResult> resultList = TSDB_CLIENT.query(SOURCE_DTO, query);
        Assert.assertTrue(CollectionUtils.isNotEmpty(resultList));
    }

    /**
     * ????????????
     */
    @Test
    public void preview() {
        List<List<Object>> preview = CLIENT.getPreview(SOURCE_DTO, SqlQueryDTO.builder().previewNum(5).tableName("loader_test_1").build());
        Assert.assertEquals(5, preview.size());
    }
}
