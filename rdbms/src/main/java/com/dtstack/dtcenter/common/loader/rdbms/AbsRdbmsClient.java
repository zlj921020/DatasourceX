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

package com.dtstack.dtcenter.common.loader.rdbms;

import com.dtstack.dtcenter.common.loader.common.DtClassThreadFactory;
import com.dtstack.dtcenter.common.loader.common.exception.ErrorCode;
import com.dtstack.dtcenter.common.loader.common.utils.CollectionUtil;
import com.dtstack.dtcenter.common.loader.common.utils.DBUtil;
import com.dtstack.dtcenter.common.loader.common.utils.ReflectUtil;
import com.dtstack.dtcenter.loader.IDownloader;
import com.dtstack.dtcenter.loader.cache.connection.CacheConnectionHelper;
import com.dtstack.dtcenter.loader.client.IClient;
import com.dtstack.dtcenter.loader.dto.ColumnMetaDTO;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.dtstack.dtcenter.loader.dto.Table;
import com.dtstack.dtcenter.loader.dto.source.ISourceDTO;
import com.dtstack.dtcenter.loader.dto.source.RdbmsSourceDTO;
import com.dtstack.dtcenter.loader.enums.ConnectionClearStatus;
import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import com.dtstack.dtcenter.loader.source.DataSourceType;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @company: www.dtstack.com
 * @Author ???Nanqi
 * @Date ???Created in 15:59 2020/1/3
 * @Description????????????
 */
@Slf4j
public abstract class AbsRdbmsClient<T> implements IClient<T> {
    private ConnFactory connFactory = getConnFactory();

    /**
     * ??????????????????
     *
     * @return
     */
    protected abstract ConnFactory getConnFactory();

    /**
     * ?????????????????????
     *
     * @return
     */
    protected abstract DataSourceType getSourceType();

    private static final String DONT_EXIST = "doesn't exist";

    private static final String SHOW_DB_SQL = "show databases";

    //????????? - ????????????????????????????????????????????????
    protected static ExecutorService executor = new ThreadPoolExecutor(5, 10, 1L, TimeUnit.MINUTES, new ArrayBlockingQueue<>(5), new DtClassThreadFactory("testConnFactory"));

    /**
     * rdbms?????????????????????????????????????????????????????????????????????
     * @param iSource
     * @return
     * @throws Exception
     */
    @Override
    public Connection getCon(ISourceDTO iSource) {
        return getCon(iSource, null);
    }

    @Override
    public Connection getCon(ISourceDTO iSource, String taskParams) {
        log.info("-------getting connection....-----");
        if (!CacheConnectionHelper.isStart()) {
            try {
                return connFactory.getConn(iSource, taskParams);
            } catch (Exception e){
                throw new DtLoaderException(e.getMessage(), e);
            }
        }

        return CacheConnectionHelper.getConnection(getSourceType().getVal(), con -> {
            try {
                return connFactory.getConn(iSource, taskParams);
            } catch (Exception e) {
                throw new DtLoaderException(e.getMessage(), e);
            }
        });
    }

    @Override
    public Boolean testCon(ISourceDTO iSource) {
        return connFactory.testConn(iSource);
    }

    /**
     * ????????????
     *
     * @param rdbmsSourceDTO
     * @param queryDTO
     * @param clearStatus
     * @return
     * @throws SQLException
     */
    public List<Map<String, Object>> executeQuery(RdbmsSourceDTO rdbmsSourceDTO, SqlQueryDTO queryDTO, Integer clearStatus) {
        try {
            // ???????????????
            if (queryDTO.getPreFields() != null) {
                return DBUtil.executeQuery(rdbmsSourceDTO.getConnection(), queryDTO.getSql(), queryDTO.getLimit(), queryDTO.getPreFields(), queryDTO.getQueryTimeout(), this::dealResult);
            }

            return DBUtil.executeQuery(rdbmsSourceDTO.getConnection(), queryDTO.getSql(), queryDTO.getLimit(), queryDTO.getQueryTimeout(), this::dealResult);
        } finally {
            DBUtil.closeDBResources(null, null, DBUtil.clearAfterGetConnection(rdbmsSourceDTO, clearStatus));
        }
    }

    @Override
    public List<Map<String, Object>> executeQuery(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        Integer clearStatus = beforeQuery(iSource, queryDTO, true);
        RdbmsSourceDTO rdbmsSourceDTO = (RdbmsSourceDTO) iSource;
        return executeQuery(rdbmsSourceDTO, queryDTO, clearStatus);
    }

    @Override
    public Boolean executeSqlWithoutResultSet(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        Integer clearStatus = beforeQuery(iSource, queryDTO, true);
        RdbmsSourceDTO rdbmsSourceDTO = (RdbmsSourceDTO) iSource;
        try {
            DBUtil.executeSqlWithoutResultSet(rdbmsSourceDTO.getConnection(), queryDTO.getSql());
        } finally {
            DBUtil.closeDBResources(null, null, DBUtil.clearAfterGetConnection(rdbmsSourceDTO, clearStatus));
        }
        return true;
    }

    /**
     * ????????????????????????
     *
     * @param iSource
     * @param queryDTO
     * @return ??????????????????????????????
     * @throws Exception
     */
    protected Integer beforeQuery(ISourceDTO iSource, SqlQueryDTO queryDTO, boolean query) {
        // ?????? SQL ????????????
        if (query && StringUtils.isBlank(queryDTO.getSql())) {
            throw new DtLoaderException("Query SQL cannot be empty");
        }

        RdbmsSourceDTO rdbmsSourceDTO = (RdbmsSourceDTO) iSource;
        // ?????? connection
        if (rdbmsSourceDTO.getConnection() == null) {
            rdbmsSourceDTO.setConnection(getCon(iSource));
            if (CacheConnectionHelper.isStart()) {
                return ConnectionClearStatus.NORMAL.getValue();
            }
            return ConnectionClearStatus.CLOSE.getValue();
        }
        return ConnectionClearStatus.NORMAL.getValue();
    }

    /**
     * ??????????????????????????????
     *
     * @param iSource
     * @param queryDTO
     * @return
     * @throws Exception
     */
    protected Integer beforeColumnQuery(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        // ?????????????????????
        Integer clearStatus = beforeQuery(iSource, queryDTO, false);
        if (queryDTO == null || StringUtils.isBlank(queryDTO.getTableName())) {
            throw new DtLoaderException("Query table name cannot be empty");
        }

        queryDTO.setColumns(CollectionUtils.isEmpty(queryDTO.getColumns()) ? Collections.singletonList("*") :
                queryDTO.getColumns());
        return clearStatus;
    }

    @Override
    public List<String> getTableList(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        Integer clearStatus = beforeQuery(iSource, queryDTO, false);
        RdbmsSourceDTO rdbmsSourceDTO = (RdbmsSourceDTO) iSource;
        ResultSet rs = null;
        List<String> tableList = new ArrayList<>();
        try {
            DatabaseMetaData meta = rdbmsSourceDTO.getConnection().getMetaData();
            if (null == queryDTO) {
                rs = meta.getTables(null, null, null, null);
            } else {
                rs = meta.getTables(null, rdbmsSourceDTO.getSchema(),
                        StringUtils.isNotBlank(queryDTO.getTableNamePattern()) ? queryDTO.getTableNamePattern() :
                                queryDTO.getTableName(),
                        DBUtil.getTableTypes(queryDTO));
            }
            while (rs.next()) {
                tableList.add(rs.getString(3));
            }
        } catch (Exception e) {
            throw new DtLoaderException(String.format("Get database table exception???%s", e.getMessage()), e);
        } finally {
            DBUtil.closeDBResources(rs, null, DBUtil.clearAfterGetConnection(rdbmsSourceDTO, clearStatus));
        }
        if (Objects.nonNull(queryDTO) && Objects.nonNull(queryDTO.getLimit())) {
            tableList = tableList.stream().limit(queryDTO.getLimit()).collect(Collectors.toList());
        }
        return tableList;
    }

    /**
     * ???????????????????????????
     *
     * @param source
     * @param queryDTO
     * @return
     * @throws Exception
     */
    @Override
    public List<String> getTableListBySchema(ISourceDTO source, SqlQueryDTO queryDTO) {
        String sql = getTableBySchemaSql(source, queryDTO);
        Integer fetchSize = ReflectUtil.fieldExists(SqlQueryDTO.class, "fetchSize") ? queryDTO.getFetchSize() : null;
        return queryWithSingleColumn(source, fetchSize, sql, 1,"get table exception according to schema...");
    }

    /**
     * ????????????sql??????????????????
     *
     * @param source      ???????????????
     * @param sql         sql??????
     * @param errMsg      ????????????
     * @return ????????????
     */
    protected List<String> queryWithSingleColumn(ISourceDTO source, Integer fetchSize, String sql, Integer columnIndex, String errMsg) {
        Integer clearStatus = beforeQuery(source, SqlQueryDTO.builder().sql(sql).build(), true);
        RdbmsSourceDTO rdbmsSourceDTO = (RdbmsSourceDTO) source;
        log.info("The SQL executed by method queryWithSingleColumn is:{}", sql);
        Statement statement = null;
        ResultSet rs = null;
        List<String> result = new ArrayList<>();
        try {
            statement = rdbmsSourceDTO.getConnection().createStatement();
            DBUtil.setFetchSize(statement, fetchSize);
            rs = statement.executeQuery(sql);
            while (rs.next()) {
                result.add(rs.getString(columnIndex == null ? 1 : columnIndex));
            }
        } catch (Exception e) {
            throw new DtLoaderException(String.format("%s:%s", errMsg, e.getMessage()), e);
        } finally {
            DBUtil.closeDBResources(rs, statement, DBUtil.clearAfterGetConnection(rdbmsSourceDTO, clearStatus));
        }
        return result;
    }

    @Override
    public List<String> getColumnClassInfo(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        Integer clearStatus = beforeColumnQuery(iSource, queryDTO);
        RdbmsSourceDTO rdbmsSourceDTO = (RdbmsSourceDTO) iSource;

        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = rdbmsSourceDTO.getConnection().createStatement();
            String queryColumnSql =
                    "select " + CollectionUtil.listToStr(queryDTO.getColumns()) + " from " + transferSchemaAndTableName(rdbmsSourceDTO, queryDTO)
                            + " where 1=2";
            rs = stmt.executeQuery(queryColumnSql);
            ResultSetMetaData rsmd = rs.getMetaData();
            int cnt = rsmd.getColumnCount();
            List<String> columnClassNameList = Lists.newArrayList();

            for (int i = 0; i < cnt; i++) {
                String columnClassName = rsmd.getColumnClassName(i + 1);
                columnClassNameList.add(columnClassName);
            }

            return columnClassNameList;
        } catch (Exception e){
            throw new DtLoaderException(e.getMessage(), e);
        } finally {
            DBUtil.closeDBResources(rs, stmt, DBUtil.clearAfterGetConnection(rdbmsSourceDTO, clearStatus));
        }
    }

    @Override
    public List<ColumnMetaDTO> getColumnMetaDataWithSql(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        Integer clearStatus = beforeQuery(iSource, queryDTO, true);
        RdbmsSourceDTO rdbmsSourceDTO = (RdbmsSourceDTO) iSource;
        Statement statement = null;
        ResultSet rs = null;
        List<ColumnMetaDTO> columns = new ArrayList<>();
        try {
            statement = rdbmsSourceDTO.getConnection().createStatement();
            String queryColumnSql = queryDTO.getSql();
            rs = statement.executeQuery(queryColumnSql);
            ResultSetMetaData rsMetaData = rs.getMetaData();
            for (int i = 0, len = rsMetaData.getColumnCount(); i < len; i++) {
                ColumnMetaDTO columnMetaDTO = new ColumnMetaDTO();
                columnMetaDTO.setKey(rsMetaData.getColumnLabel(i + 1));
                columnMetaDTO.setType(doDealType(rsMetaData, i));
                columnMetaDTO.setPart(false);
                // ??????????????????
                if (columnMetaDTO.getType().equalsIgnoreCase("decimal")
                        || columnMetaDTO.getType().equalsIgnoreCase("float")
                        || columnMetaDTO.getType().equalsIgnoreCase("double")
                        || columnMetaDTO.getType().equalsIgnoreCase("numeric")) {
                    columnMetaDTO.setScale(rsMetaData.getScale(i + 1));
                    columnMetaDTO.setPrecision(rsMetaData.getPrecision(i + 1));
                }

                columns.add(columnMetaDTO);
            }
            return columns;

        } catch (SQLException e) {
            if (e.getMessage().contains(DONT_EXIST)) {
                throw new DtLoaderException(String.format(queryDTO.getTableName() + "table not exist,%s", e.getMessage()), e);
            } else {
                throw new DtLoaderException(String.format("Failed to get the meta information of the fields of the table: %s. Please contact the DBA to check the database and table information: %s",
                        queryDTO.getTableName(), e.getMessage()), e);
            }
        } finally {
            DBUtil.closeDBResources(rs, statement, DBUtil.clearAfterGetConnection(rdbmsSourceDTO, clearStatus));
        }
    }

    @Override
    public List<ColumnMetaDTO> getColumnMetaData(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        Integer clearStatus = beforeColumnQuery(iSource, queryDTO);
        RdbmsSourceDTO rdbmsSourceDTO = (RdbmsSourceDTO) iSource;
        Statement statement = null;
        ResultSet rs = null;
        List<ColumnMetaDTO> columns = new ArrayList<>();
        try {
            statement = rdbmsSourceDTO.getConnection().createStatement();
            String queryColumnSql =
                    "select " + CollectionUtil.listToStr(queryDTO.getColumns()) + " from " + transferSchemaAndTableName(rdbmsSourceDTO, queryDTO) + " where 1=2";

            rs = statement.executeQuery(queryColumnSql);
            ResultSetMetaData rsMetaData = rs.getMetaData();
            for (int i = 0, len = rsMetaData.getColumnCount(); i < len; i++) {
                ColumnMetaDTO columnMetaDTO = new ColumnMetaDTO();
                columnMetaDTO.setKey(rsMetaData.getColumnName(i + 1));
                columnMetaDTO.setType(doDealType(rsMetaData, i));
                columnMetaDTO.setPart(false);
                // ??????????????????
                if (columnMetaDTO.getType().equalsIgnoreCase("decimal")
                        || columnMetaDTO.getType().equalsIgnoreCase("float")
                        || columnMetaDTO.getType().equalsIgnoreCase("double")
                        || columnMetaDTO.getType().equalsIgnoreCase("numeric")) {
                    columnMetaDTO.setScale(rsMetaData.getScale(i + 1));
                    columnMetaDTO.setPrecision(rsMetaData.getPrecision(i + 1));
                }

                columns.add(columnMetaDTO);
            }
        } catch (SQLException e) {
            if (e.getMessage().contains(DONT_EXIST)) {
                throw new DtLoaderException(String.format(queryDTO.getTableName() + "table not exist,%s", e.getMessage()), e);
            } else {
                throw new DtLoaderException(String.format("Failed to get the meta information of the fields of the table: %s. Please contact the DBA to check the database and table information: %s",
                        queryDTO.getTableName(), e.getMessage()), e);
            }
        } finally {
            DBUtil.closeDBResources(rs, statement, DBUtil.clearAfterGetConnection(rdbmsSourceDTO, clearStatus));
        }

        //??????????????????
        Map<String, String> columnComments = getColumnComments(rdbmsSourceDTO, queryDTO);
        if (Objects.isNull(columnComments)) {
            return columns;
        }
        for (ColumnMetaDTO columnMetaDTO : columns) {
            if (columnComments.containsKey(columnMetaDTO.getKey())) {
                columnMetaDTO.setComment(columnComments.get(columnMetaDTO.getKey()));
            }
        }
        return columns;

    }

    @Override
    public List<ColumnMetaDTO> getFlinkColumnMetaData(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        return getColumnMetaData(iSource, queryDTO);
    }

    @Override
    public String getTableMetaComment(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        return "";
    }

    /**
     * rdbms????????????
     * @param iSource
     * @param queryDTO
     * @return
     * @throws Exception
     */
    @Override
    public List<List<Object>> getPreview(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        Integer clearStatus = beforeColumnQuery(iSource, queryDTO);
        RdbmsSourceDTO rdbmsSourceDTO = (RdbmsSourceDTO) iSource;
        List<List<Object>> previewList = new ArrayList<>();
        if (StringUtils.isBlank(queryDTO.getTableName())) {
            return previewList;
        }
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = rdbmsSourceDTO.getConnection().createStatement();
            //??????sql???????????????100???
            String querySql = dealSql(rdbmsSourceDTO, queryDTO);
            if (queryDTO.getPreviewNum() != null) {
                stmt.setMaxRows(queryDTO.getPreviewNum());
            }
            rs = stmt.executeQuery(querySql);
            ResultSetMetaData rsmd = rs.getMetaData();
            //??????????????????
            List<Object> metaDataList = Lists.newArrayList();
            //????????????
            int len = rsmd.getColumnCount();
            for (int i = 0; i < len; i++) {
                metaDataList.add(rsmd.getColumnLabel(i + 1));
            }
            previewList.add(metaDataList);
            while (rs.next()){
                //??????columnData????????????????????????
                ArrayList<Object> columnData = Lists.newArrayList();
                for (int i = 0; i < len; i++) {
                    Object result = dealResult(rs.getObject(i + 1));
                    columnData.add(result);
                }
                previewList.add(columnData);
            }
        } catch (Exception e) {
            throw new DtLoaderException(e.getMessage(), e);
        } finally {
            DBUtil.closeDBResources(rs, stmt, DBUtil.clearAfterGetConnection(rdbmsSourceDTO, clearStatus));
        }
        return previewList;
    }

    /**
     * ??????jdbc????????????
     * @param result ????????????
     * @return ??????????????????
     */
    protected Object dealResult(Object result){
        return result;
    }

    /**
     * ??????sql??????????????????
     * @param sqlQueryDTO ????????????
     * @return ??????????????????sql
     */
    protected String dealSql(ISourceDTO sourceDTO, SqlQueryDTO sqlQueryDTO){
        return "select * from " + transferSchemaAndTableName(sourceDTO, sqlQueryDTO);
    }

    /**
     * ?????? schema ??? ?????????????????? SqlQueryDTO ?????? schema
     *
     * @param sourceDTO   ?????????????????????
     * @param sqlQueryDTO ????????????
     * @return ???????????? schema ??? table
     */
    protected String transferSchemaAndTableName(ISourceDTO sourceDTO, SqlQueryDTO sqlQueryDTO) {
        RdbmsSourceDTO rdbmsSourceDTO = (RdbmsSourceDTO) sourceDTO;
        String schema = StringUtils.isNotBlank(sqlQueryDTO.getSchema()) ? sqlQueryDTO.getSchema() : rdbmsSourceDTO.getSchema();
        return transferSchemaAndTableName(schema, sqlQueryDTO.getTableName());
    }

    /**
     * ?????????????????? sql
     *
     * @param limit ????????????
     * @return ???????????? sql
     */
    protected String limitSql(Integer limit) {
        if (Objects.isNull(limit) || limit < 1) {
            throw new DtLoaderException(String.format("limit number [%s] is error", limit));
        }
        return " limit " + limit;
    }

    /**
     * ??????schema?????????
     *
     * @param schema
     * @param tableName
     * @return
     */
    protected String transferSchemaAndTableName(String schema,String tableName) {
        return transferTableName(tableName);
    }

    /**
     * ????????????
     *
     * @param tableName
     * @return
     */
    @Deprecated
    protected String transferTableName(String tableName) {
        return tableName;
    }

    /**
     * ??????????????????
     */
    protected String doDealType(ResultSetMetaData rsMetaData, Integer los) throws SQLException {
        return rsMetaData.getColumnTypeName(los + 1);
    }

    protected Map<String, String> getColumnComments(RdbmsSourceDTO sourceDTO, SqlQueryDTO queryDTO) {
        return Collections.emptyMap();
    }

    /**
     * ??????schema??????????????????????????????????????????????????????????????????????????????
     *
     * @param queryDTO ??????queryDTO
     * @return sql??????
     */
    protected String getTableBySchemaSql(ISourceDTO sourceDTO, SqlQueryDTO queryDTO) {
        throw new DtLoaderException(ErrorCode.NOT_SUPPORT.getDesc());
    }

    @Override
    public IDownloader getDownloader(ISourceDTO source, SqlQueryDTO queryDTO) throws Exception {
        throw new DtLoaderException(ErrorCode.NOT_SUPPORT.getDesc());
    }

    @Override
    public IDownloader getDownloader(ISourceDTO source, String sql, Integer pageSize) throws Exception {
        throw new DtLoaderException(ErrorCode.NOT_SUPPORT.getDesc());
    }

    @Override
    public List<String> getAllDatabases(ISourceDTO source, SqlQueryDTO queryDTO){
        // ???????????????????????????show databases ??????
        String sql = getShowDbSql();
        return queryWithSingleColumn(source, null, sql, 1, "get All database exception");
    }

    @Override
    public List<String> getRootDatabases(ISourceDTO source, SqlQueryDTO queryDTO) {
        throw new DtLoaderException(ErrorCode.NOT_SUPPORT.getDesc());
    }

    @Override
    public String getCreateTableSql(ISourceDTO source, SqlQueryDTO queryDTO) {
        Integer clearStatus = beforeQuery(source, queryDTO, false);
        RdbmsSourceDTO rdbmsSourceDTO = (RdbmsSourceDTO) source;

        // ???????????????????????????show databases ??????
        String tableName ;
        if (StringUtils.isNotEmpty(rdbmsSourceDTO.getSchema())) {
            tableName = rdbmsSourceDTO.getSchema() + "." + queryDTO.getTableName();
        } else {
            tableName = queryDTO.getTableName();
        }
        String sql = queryDTO.getSql()==null?"show create table "+tableName:queryDTO.getSql();
        Statement statement = null;
        ResultSet rs = null;
        String createTableSql =null;
        try {
            statement = rdbmsSourceDTO.getConnection().createStatement();
            rs = statement.executeQuery(sql);
            int columnSize = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                createTableSql = rs.getString(columnSize == 1 ? 1 : 2);
                break;
            }
        } catch (Exception e) {
            throw new DtLoaderException(String.format("failed to get the create table sql???%s", e.getMessage()), e);
        } finally {
            DBUtil.closeDBResources(rs, statement, DBUtil.clearAfterGetConnection(rdbmsSourceDTO, clearStatus));
        }
        return createTableSql;
    }

    @Override
    public List<ColumnMetaDTO> getPartitionColumn(ISourceDTO source, SqlQueryDTO queryDTO) {
        return Collections.emptyList();
    }

    @Override
    public Table getTable(ISourceDTO source, SqlQueryDTO queryDTO) {
        Integer clearStatus = beforeQuery(source, queryDTO, false);
        RdbmsSourceDTO rdbmsSourceDTO = (RdbmsSourceDTO) source;
        Table table = new Table();
        try {
            List<ColumnMetaDTO> columnMetaData = getColumnMetaData(source, queryDTO);
            String tableComment = getTableMetaComment(source, queryDTO);
            table.setColumns(columnMetaData);
            table.setName(queryDTO.getTableName());
            table.setComment(tableComment);
        } catch (Exception e) {
            throw new DtLoaderException(String.format("SQL executed exception: %s", e.getMessage()), e);
        } finally {
            DBUtil.closeDBResources(null, null, DBUtil.clearAfterGetConnection(rdbmsSourceDTO, clearStatus));
        }
        return table;
    }

    /**
     * ???????????? ?????????/schema sql??????
     * @return
     */
    protected String getShowDbSql(){
        return SHOW_DB_SQL;
    }

    @Override
    public String getCurrentDatabase(ISourceDTO source) {
        // ????????????schema????????????sql
        String sql = getCurrentDbSql();
        List<String> result = queryWithSingleColumn(source, null, sql, 1, "failed to get the currently used database");
        if (CollectionUtils.isEmpty(result)) {
            throw new DtLoaderException("failed to get the currently used database");
        }
        return result.get(0);
    }

    /**
     * ??????????????????db???sql???????????????????????????????????????????????????
     * @return ?????????sql
     */
    protected String getCurrentDbSql() {
        throw new DtLoaderException(ErrorCode.NOT_SUPPORT.getDesc());
    }

    @Override
    public Boolean createDatabase(ISourceDTO source, String dbName, String comment) {
        if (StringUtils.isBlank(dbName)) {
            throw new DtLoaderException("database or schema cannot be empty");
        }
        String createSchemaSql = getCreateDatabaseSql(dbName, comment);
        return executeSqlWithoutResultSet(source, SqlQueryDTO.builder().sql(createSchemaSql).build());
    }

    @Override
    public List<String> getCatalogs(ISourceDTO source) {
        String showCatalogsSql = getCatalogSql();
        return queryWithSingleColumn(source, null, showCatalogsSql, 1, "failed to get data source directory list");
    }

    /**
     * ??????????????????sql?????????????????????????????????????????????
     * @param dbName ??????
     * @param comment ??????
     * @return sql
     */
    protected String getCreateDatabaseSql(String dbName, String comment) {
        throw new DtLoaderException(ErrorCode.NOT_SUPPORT.getDesc());
    }


    @Override
    public Boolean isDatabaseExists(ISourceDTO source, String dbName) {
        throw new DtLoaderException(ErrorCode.NOT_SUPPORT.getDesc());
    }

    @Override
    public Boolean isTableExistsInDatabase(ISourceDTO source, String tableName, String dbName) {
        throw new DtLoaderException(ErrorCode.NOT_SUPPORT.getDesc());
    }

    /**
     * ???????????????/????????????????????????sql?????????????????????????????????????????????
     * @return sql
     */
    protected String getCatalogSql() {
        throw new DtLoaderException(ErrorCode.NOT_SUPPORT.getDesc());
    }

    /**
     * ???????????????????????? %
     *
     * @param str ???????????? % ????????????
     * @return ?????? % ???????????????
     */
    protected String addPercentSign(String str) {
        return "%" + str + "%";
    }

    @Override
    public String getVersion(ISourceDTO source) {
        String showVersionSql = getVersionSql();
        List<String> result = queryWithSingleColumn(source, null, showVersionSql, 1, "failed to get data source version");
        return CollectionUtils.isNotEmpty(result) ? result.get(0) : "";
    }

    /**
     * ????????????????????????sql?????????????????????????????????????????????
     *
     * @return ????????????????????? sql
     */
    protected String getVersionSql() {
        throw new DtLoaderException(ErrorCode.NOT_SUPPORT.getDesc());
    }

    @Override
    public List<String> listFileNames(ISourceDTO sourceDTO, String path, Boolean includeDir, Boolean recursive, Integer maxNum, String regexStr) {
        throw new DtLoaderException(ErrorCode.NOT_SUPPORT.getDesc());
    }
}
