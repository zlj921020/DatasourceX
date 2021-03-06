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

package com.dtstack.dtcenter.common.loader.impala;

import com.dtstack.dtcenter.common.loader.common.exception.ErrorCode;
import com.dtstack.dtcenter.loader.IDownloader;
import com.dtstack.dtcenter.loader.dto.Column;
import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * impala表数据下载器
 *
 * @author ：wangchuan
 * date：Created in 上午9:53 2021/4/6
 * company: www.dtstack.com
 */
@Slf4j
public class ImpalaDownload implements IDownloader {

    /**
     * 执行的 sql
     */
    private final String sql;

    /**
     * 数据库连接
     */
    private final Connection connection;

    /**
     * 连接操作对象
     */
    private Statement statement;

    /**
     * 页标
     */
    private int pageIndex = 0;

    /**
     * 每页数据条数
     */
    private int pageSize = 100;

    /**
     * 当前读取条数
     */
    private int readLine = 0;

    /**
     * 总数据条数
     */
    private int totalLine = 0;

    /**
     * 列字段
     */
    private List<Column> columnNames;

    /**
     * 详细的数据信息
     */
    private List<List<String>> pageInfo;

    /**
     * 表字段条数
     */
    private int columnCount;

    /**
     * 是否已经调用 configure 方法
     */
    private boolean isConfigure = false;

    public ImpalaDownload(Connection connection, String sql) {
        this.connection = connection;
        this.sql = sql;
    }

    @Override
    public boolean configure() throws Exception {
        if (BooleanUtils.isTrue(isConfigure)) {
            // 避免 configure 方法重复调用
            return true;
        }
        if (null == connection || StringUtils.isEmpty(sql)) {
            throw new DtLoaderException("impala connection acquisition failed or execution SQL is empty");
        }
        pageInfo = new ArrayList<>(pageSize);
        statement = connection.createStatement();
        String countSQL = String.format("SELECT COUNT(*) FROM (%s) temp", sql);
        try (ResultSet resultSet = statement.executeQuery(countSQL);) {
            while (resultSet.next()) {
                // 获取总行数
                totalLine = resultSet.getInt(1);
            }
        }
        // 获取列信息
        String showColumns = String.format("SELECT * FROM (%s) t LIMIT 1", sql);
        try (ResultSet resultSet = statement.executeQuery(showColumns)) {
            columnNames = new ArrayList<>();
            columnCount = resultSet.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                Column column = new Column();
                column.setName(resultSet.getMetaData().getColumnName(i));
                column.setType(resultSet.getMetaData().getColumnTypeName(i));
                column.setIndex(i);
                columnNames.add(column);
            }
        }
        isConfigure = true;
        log.info("impalaDownload: executed SQL:{}, totalLine:{}, columnNames:{}, columnCount{}", sql, totalLine, columnNames, columnCount);
        return true;
    }

    @Override
    public List<String> getMetaInfo() {
        if (CollectionUtils.isEmpty(columnNames)) {
            return Collections.emptyList();
        }
        return columnNames.stream().map(Column::getName).collect(Collectors.toList());
    }

    @Override
    public Object readNext() {
        List<String> tempCache;
        if (CollectionUtils.isNotEmpty(pageInfo) && pageIndex < pageSize) {
            tempCache = pageInfo.get(pageIndex);
            if (pageIndex == pageSize - 1) {
                // 分页读取完成
                pageIndex = 0;
                pageInfo = new ArrayList<>(100);
            } else {
                // 没有页标自增
                pageIndex++;
            }

        } else {
            // 读取下一页
            String limitSQL = String.format("SELECT * FROM (%s) t ORDER BY %s LIMIT %s OFFSET %s", sql, columnNames.stream().map(Column::getName).collect(Collectors.joining(",")), pageSize, readLine);
            try (ResultSet resultSet = statement.executeQuery(limitSQL);) {
                List<List<String>> pageTemp = new ArrayList<>(100);
                while (resultSet.next()) {
                    List<String> columns = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(resultSet.getString(i));
                    }
                    pageTemp.add(columns);
                }
                pageInfo = pageTemp;
            } catch (Exception e) {
                throw new DtLoaderException(String.format("read the next page, execute SQL exception, the reason is：%s", e.getMessage()), e);
            }
            pageIndex = 0;
            if (CollectionUtils.isNotEmpty(pageInfo) && pageInfo.size() < pageSize) {
                // 最后一页面 不足一页
                pageSize = pageInfo.size();
            }
            tempCache = pageInfo.get(pageIndex);
            pageIndex++;
        }
        // 读取总条数 + 1
        readLine++;
        return tempCache;
    }

    @Override
    public boolean reachedEnd() {
        return totalLine == readLine;
    }

    @Override
    public boolean close() throws Exception {
        if (Objects.nonNull(statement)) {
            statement.close();
        }
        if (Objects.nonNull(connection)) {
            connection.close();
        }
        return true;
    }

    @Override
    public String getFileName() {
        throw new DtLoaderException(ErrorCode.NOT_SUPPORT.getDesc());
    }

    @Override
    public List<String> getContainers() {
        throw new DtLoaderException(ErrorCode.NOT_SUPPORT.getDesc());
    }
}
