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

package com.dtstack.dtcenter.common.loader.hbase;

import com.dtstack.dtcenter.loader.client.IHbase;
import com.dtstack.dtcenter.loader.dto.HbaseQueryDTO;
import com.dtstack.dtcenter.loader.dto.filter.TimestampFilter;
import com.dtstack.dtcenter.loader.dto.source.HbaseSourceDTO;
import com.dtstack.dtcenter.loader.dto.source.ISourceDTO;
import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.NamespaceNotFoundException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.CompareFilter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.PageFilter;
import org.apache.hadoop.hbase.filter.RegexStringComparator;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * hbase ?????????????????????hbase?????????????????????
 *
 * @author ???wangchuan
 * date???Created in 10:23 ?????? 2020/12/2
 * company: www.dtstack.com
 */
@Slf4j
public class HbaseClientSpecial implements IHbase {

    // ????????????????????????
    private static final Integer MAX_PREVIEW_NUM = 5000;

    // ????????????????????????
    private static final Integer DEFAULT_PREVIEW_NUM = 100;

    // rowkey
    private static final String ROWKEY = "rowkey";

    // ??????:??????
    private static final String FAMILY_QUALIFIER = "%s:%s";

    // ???????????????
    private static final String TIMESTAMP = "timestamp";

    @Override
    public Boolean isDbExists(ISourceDTO source, String namespace) {
        HbaseSourceDTO hbaseSourceDTO = (HbaseSourceDTO) source;
        Connection connection = null;
        Admin admin = null;
        try {
            //??????hbase??????
            connection = HbaseConnFactory.getHbaseConn(hbaseSourceDTO);
            admin = connection.getAdmin();
            NamespaceDescriptor namespaceDescriptor = admin.getNamespaceDescriptor(namespace);
            if (Objects.nonNull(namespaceDescriptor)) {
                return true;
            }
        } catch (NamespaceNotFoundException namespaceNotFoundException) {
            log.error("namespace [{}] not found!", namespace);
        } catch (Exception e) {
            throw new DtLoaderException(String.format("get namespace exception, namespace???'%s', %s", namespace, e.getMessage()), e);
        } finally {
            close(admin);
            closeConnection(connection, hbaseSourceDTO);
            HbaseClient.destroyProperty();
        }
        return false;
    }

    @Override
    public Boolean createHbaseTable(ISourceDTO source, String tbName, String[] colFamily) {
        return createHbaseTable(source, null, tbName, colFamily);
    }

    @Override
    public Boolean createHbaseTable(ISourceDTO source, String namespace, String tbName, String[] colFamily) {
        if (StringUtils.isNotBlank(namespace) && !tbName.contains(":")) {
            tbName = String.format("%s:%s", namespace, tbName);
        }
        HbaseSourceDTO hbaseSourceDTO = (HbaseSourceDTO) source;
        TableName tableName = TableName.valueOf(tbName);
        Connection connection = null;
        Admin admin = null;
        try {
            //??????hbase??????
            connection = HbaseConnFactory.getHbaseConn(hbaseSourceDTO);
            admin = connection.getAdmin();
            if (admin.tableExists(tableName)) {
                throw new DtLoaderException(String.format("The current table already exists???:'%s'", tbName));
            } else {
                HTableDescriptor hTableDescriptor = new HTableDescriptor(tableName);
                for (String str : colFamily) {
                    HColumnDescriptor hColumnDescriptor = new HColumnDescriptor(str);
                    hTableDescriptor.addFamily(hColumnDescriptor);
                }
                admin.createTable(hTableDescriptor);
                log.info("hbase create table '{}' success!", tableName);
            }
        } catch (DtLoaderException e) {
            throw e;
        } catch (Exception e) {
            throw new DtLoaderException(String.format("hbase failed to create table???namespace???'%s'???table name???'%s'???column family???'%s'", namespace, tbName, Arrays.toString(colFamily)), e);
        } finally {
            close(admin);
            closeConnection(connection, hbaseSourceDTO);
            HbaseClient.destroyProperty();
        }
        return true;
    }

    @Override
    public Boolean deleteHbaseTable(ISourceDTO source, String tableName) {
        HbaseSourceDTO hbaseSourceDTO = (HbaseSourceDTO) source;
        TableName tbName = TableName.valueOf(tableName);
        Connection connection = null;
        Admin admin = null;
        try {
            //??????hbase??????
            connection = HbaseConnFactory.getHbaseConn(hbaseSourceDTO);
            admin = connection.getAdmin();
            admin.disableTable(tbName);
            admin.deleteTable(tbName);
            log.info("delete hbase table success, table name {}", tbName);
        } catch (Exception e) {
            log.error("delete hbase table error, table name: {}", tbName, e);
            throw new DtLoaderException(String.format("hbase failed to delete table???table name: %s", tableName), e);
        } finally {
            close(admin);
            closeConnection(connection, hbaseSourceDTO);
            HbaseClient.destroyProperty();
        }
        return true;
    }

    @Override
    public Boolean deleteHbaseTable(ISourceDTO source, String namespace, String tableName) {
        if (StringUtils.isNotBlank(namespace) && !tableName.contains(":")) {
            tableName = String.format("%s:%s", namespace, tableName);
        }
        return deleteHbaseTable(source, tableName);
    }

    @Override
    public List<String> scanByRegex(ISourceDTO source, String tbName, String regex) {
        HbaseSourceDTO hbaseSourceDTO = (HbaseSourceDTO) source;
        Connection connection = null;
        Table table = null;
        ResultScanner rs = null;
        List<String> results = Lists.newArrayList();
        try {
            //??????hbase??????
            connection = HbaseConnFactory.getHbaseConn(hbaseSourceDTO);
            table = connection.getTable(TableName.valueOf(tbName));
            Scan scan = new Scan();
            org.apache.hadoop.hbase.filter.Filter rowFilter = new org.apache.hadoop.hbase.filter.RowFilter(CompareFilter.CompareOp.EQUAL, new RegexStringComparator(regex));
            scan.setFilter(rowFilter);
            rs = table.getScanner(scan);
            for (Result r : rs) {
                results.add(Bytes.toString(r.getRow()));
            }
        } catch (DtLoaderException e) {
            throw e;
        } catch (Exception e) {
            throw new DtLoaderException(String.format("Hbase scans data abnormally according to regular???,regex???%s", regex), e);
        } finally {
            close(rs, table);
            closeConnection(connection, hbaseSourceDTO);
            HbaseClient.destroyProperty();
        }
        return results;
    }

    @Override
    public Boolean deleteByRowKey(ISourceDTO source, String tbName, String family, String qualifier, List<String> rowKeys) {
        HbaseSourceDTO hbaseSourceDTO = (HbaseSourceDTO) source;
        Connection connection = null;
        Table table = null;
        if (CollectionUtils.isEmpty(rowKeys)) {
            throw new DtLoaderException("The rowKey to be deleted cannot be empty???");
        }
        try {
            //??????hbase??????
            connection = HbaseConnFactory.getHbaseConn(hbaseSourceDTO);
            table = connection.getTable(TableName.valueOf(tbName));
            for (String rowKey : rowKeys) {
                Delete delete = new Delete(Bytes.toBytes(rowKey));
                delete.addColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier));
                table.delete(delete);
                log.info("delete hbase rowKey success , rowKey {}", rowKey);
            }
            return true;
        } catch (DtLoaderException e) {
            throw e;
        } catch (Exception e) {
            throw new DtLoaderException(String.format("hbase delete data exception! rowKeys??? %s,%s", rowKeys, e.getMessage()), e);
        } finally {
            close(table);
            closeConnection(connection, hbaseSourceDTO);
            HbaseClient.destroyProperty();
        }
    }

    @Override
    public Boolean putRow(ISourceDTO source, String tableName, String rowKey, String family, String qualifier, String data) {
        HbaseSourceDTO hbaseSourceDTO = (HbaseSourceDTO) source;
        Connection connection = null;
        Table table = null;
        try {
            //??????hbase??????
            connection = HbaseConnFactory.getHbaseConn(hbaseSourceDTO);
            table = connection.getTable(TableName.valueOf(tableName));
            Put put = new Put(Bytes.toBytes(rowKey));
            put.addColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier), Bytes.toBytes(data));
            table.put(put);
            return true;
        } catch (DtLoaderException e) {
            throw e;
        } catch (Exception e) {
            throw new DtLoaderException(String.format("hbase insert data exception! rowKey??? %s??? data??? %s, error: %s", rowKey, data, e.getMessage()), e);
        } finally {
            close(table);
            closeConnection(connection, hbaseSourceDTO);
            HbaseClient.destroyProperty();
        }
    }

    @Override
    public String getRow(ISourceDTO source, String tableName, String rowKey, String family, String qualifier) {
        HbaseSourceDTO hbaseSourceDTO = (HbaseSourceDTO) source;
        Connection connection = null;
        Table table = null;
        String row = null;
        try {
            //??????hbase??????
            connection = HbaseConnFactory.getHbaseConn(hbaseSourceDTO);
            table = connection.getTable(TableName.valueOf(tableName));
            Get get = new Get(Bytes.toBytes(rowKey));
            get.addColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier));
            Result result = table.get(get);
            row =  Bytes.toString(result.getValue(Bytes.toBytes(family), Bytes.toBytes(qualifier)));
        } catch (DtLoaderException e) {
            throw e;
        } catch (Exception e) {
            throw new DtLoaderException(String.format("Hbase gets data exception! rowKey??? %s , %s", rowKey, e.getMessage()), e);
        } finally {
            close(table);
            closeConnection(connection, hbaseSourceDTO);
            HbaseClient.destroyProperty();
        }
        return row;
    }

    @Override
    public List<List<String>> preview(ISourceDTO source, String tableName, Integer previewNum) {
        return preview(source, tableName, Maps.newHashMap(), previewNum);
    }

    @Override
    public List<List<String>> preview(ISourceDTO source, String tableName, List<String> familyList, Integer previewNum) {
        Map<String, List<String>> familyQualifierMap = Maps.newHashMap();
        if (CollectionUtils.isNotEmpty(familyList)) {
            familyList.forEach(family -> familyQualifierMap.put(family, null));
        }
        return preview(source, tableName, familyQualifierMap, previewNum);
    }

    @Override
    public List<List<String>> preview(ISourceDTO source, String tableName, Map<String, List<String>> familyQualifierMap, Integer previewNum) {
        HbaseSourceDTO hbaseSourceDTO = (HbaseSourceDTO) source;
        Connection connection = null;
        Table table = null;
        ResultScanner rs = null;
        List<List<String>> previewList = Lists.newArrayList();
        try {
            // ??????hbase??????
            connection = HbaseConnFactory.getHbaseConn(hbaseSourceDTO);
            table = connection.getTable(TableName.valueOf(tableName));
            Scan scan = new Scan();
            // ????????????????????????????????? 5000????????? 100
            if (Objects.isNull(previewNum) || previewNum <= 0) {
                previewNum = DEFAULT_PREVIEW_NUM;
            } else if (previewNum > MAX_PREVIEW_NUM) {
                previewNum = MAX_PREVIEW_NUM;
            }
            // ?????????????????????????????????
            if (MapUtils.isNotEmpty(familyQualifierMap)) {
                for (String family : familyQualifierMap.keySet()) {
                    List<String> qualifiers = familyQualifierMap.get(family);
                    if (CollectionUtils.isNotEmpty(qualifiers)) {
                        for (String qualifier : qualifiers) {
                            scan.addColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier));
                        }
                    } else {
                        scan.addFamily(Bytes.toBytes(family));
                    }
                }
            }
            // ??????????????????????????????
            scan.setMaxResultSize(previewNum);
            scan.setFilter(new PageFilter(previewNum));
            rs = table.getScanner(scan);
            List<Result> results = Lists.newArrayList();
            for (Result row : rs) {
                if (CollectionUtils.isEmpty(row.listCells())) {
                    continue;
                }
                results.add(row);
                if (results.size() >= previewNum) {
                    break;
                }
            }
            if (CollectionUtils.isEmpty(results)) {
                return previewList;
            }
            // ????????????????????????
            Map<String, List<String>> columnValueMap = Maps.newHashMap();
            // rowKey ??????
            List<String> rowKeyList = Lists.newArrayList();
            // timestamp ??????????????????rowkey ???????????????????????????????????????rowkey???timestamp
            List<String> timeStampList = Lists.newArrayList();
            columnValueMap.put(ROWKEY, rowKeyList);
            columnValueMap.put(TIMESTAMP, timeStampList);
            // ??????
            int rowNum = 0;
            for (Result result : results) {
                rowNum++;
                // ????????????????????????cells????????????????????????????????????cell??????????????????????????????????????????
                List<Cell> cells = result.listCells();
                long timestamp = 0L;
                String rowKey = null;
                for (Cell cell : cells) {
                    rowKey = Bytes.toString(cell.getRowArray(), cell.getRowOffset(), cell.getRowLength());
                    String family = Bytes.toString(cell.getFamilyArray(), cell.getFamilyOffset(), cell.getFamilyLength());
                    String qualifier = Bytes.toString(cell.getQualifierArray(), cell.getQualifierOffset(), cell.getQualifierLength());
                    String column = String.format(FAMILY_QUALIFIER, family, qualifier);
                    List<String> columnList = columnValueMap.get(column);
                    // ????????????????????? null
                    if (Objects.isNull(columnList)) {
                        columnList = Lists.newArrayList();
                        for (int i = 0; i < rowNum - 1; i++) {
                            columnList.add(null);
                        }
                        columnValueMap.put(column, columnList);
                    }
                    String value = Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength());
                    columnList.add(value);
                    //???????????????????????????
                    if (cell.getTimestamp() > timestamp) {
                        timestamp = cell.getTimestamp();
                    }
                }
                List<String> rowKeyColumnList = columnValueMap.get(ROWKEY);
                List<String> timestampColumnList = columnValueMap.get(TIMESTAMP);
                rowKeyColumnList.add(rowKey);
                timestampColumnList.add(String.valueOf(timestamp));
                int finalRowNum = rowNum;
                // ?????????????????????null???
                columnValueMap.forEach((key, value) -> {
                    if (value.size() < finalRowNum) {
                        value.add(null);
                    }
                });
            }
            List<String> columnMetaDatas = new ArrayList<>(columnValueMap.keySet());
            columnMetaDatas.remove(ROWKEY);
            columnMetaDatas.remove(TIMESTAMP);
            // ????????????rowKey???????????????timestamp???????????????
            Collections.sort(columnMetaDatas);
            columnMetaDatas.add(0, ROWKEY);
            columnMetaDatas.add(TIMESTAMP);
            previewList.add(columnMetaDatas);
            for (int i = 0; i < rowNum; i++) {
                List<String> row = Lists.newArrayList();
                for (String columnMetaData : columnMetaDatas) {
                    row.add(columnValueMap.get(columnMetaData).get(i));
                }
                previewList.add(row);
            }
            return previewList;
        } catch (Exception e) {
            throw new DtLoaderException(String.format("Data preview failed,%s", e.getMessage()), e);
        } finally {
            close(table, rs);
            closeConnection(connection, hbaseSourceDTO);
            HbaseClient.destroyProperty();
        }
    }

    @Override
    public List<Map<String, Object>> executeQuery(ISourceDTO source, HbaseQueryDTO hbaseQueryDTO, TimestampFilter timestampFilter) {
        HbaseSourceDTO hbaseSourceDTO = (HbaseSourceDTO) source;
        Connection connection = null;
        Table table = null;
        ResultScanner rs = null;
        List<Result> results = Lists.newArrayList();
        List<Map<String, Object>> executeResult = Lists.newArrayList();
        try {
            // ??????hbase??????
            connection = HbaseConnFactory.getHbaseConn(hbaseSourceDTO);
            List<String> columns = hbaseQueryDTO.getColumns();
            // ??????????????? hbase TableName
            TableName tableName = TableName.valueOf(hbaseQueryDTO.getTableName());
            table = connection.getTable(tableName);
            Scan scan = new Scan();
            // ?????? hbase ?????????????????? --> ??????:??????
            if (CollectionUtils.isNotEmpty(columns)) {
                for (String column : columns) {
                    String[] familyAndQualifier = column.split(":");
                    if (familyAndQualifier.length < 2) {
                        continue;
                    }
                    scan.addColumn(Bytes.toBytes(familyAndQualifier[0]), Bytes.toBytes(familyAndQualifier[1]));
                }
            }
            // ?????? common-loader ??????????????????????????????????????? hbase ?????? filter
            com.dtstack.dtcenter.loader.dto.filter.Filter loaderFilter = hbaseQueryDTO.getFilter();
            if (Objects.nonNull(loaderFilter)) {
                if (loaderFilter instanceof com.dtstack.dtcenter.loader.dto.filter.FilterList) {
                    com.dtstack.dtcenter.loader.dto.filter.FilterList loaderFilterList = (com.dtstack.dtcenter.loader.dto.filter.FilterList) loaderFilter;
                    FilterList hbaseFilterList = new FilterList(convertOp(loaderFilterList.getOperator()));
                    convertFilter(loaderFilterList, hbaseFilterList);
                    scan.setFilter(hbaseFilterList);
                } else {
                    scan.setFilter(FilterType.get(loaderFilter));
                }
            }
            // ???????????? rowKey
            if (StringUtils.isNotBlank(hbaseQueryDTO.getStartRowKey())) {
                scan.setStartRow(Bytes.toBytes(hbaseQueryDTO.getStartRowKey()));
            }
            // ???????????? rowKey
            if (StringUtils.isNotBlank(hbaseQueryDTO.getEndRowKey())) {
                scan.setStopRow(Bytes.toBytes(hbaseQueryDTO.getEndRowKey()));
            }
            // ?????? pageFilter ?????????????????? region ???????????????????????????????????? limit ??????
            long limit = Objects.isNull(hbaseQueryDTO.getLimit()) ? Long.MAX_VALUE : hbaseQueryDTO.getLimit();
            scan.setMaxResultSize(limit);
            // ???????????????????????????
            if (Objects.nonNull(timestampFilter)) {
                HbaseClient.fillTimestampFilter(scan, timestampFilter);
            }
            rs = table.getScanner(scan);
            for (Result row : rs) {
                if (CollectionUtils.isEmpty(row.listCells())) {
                    continue;
                }
                results.add(row);
                if (results.size() >= limit) {
                    break;
                }
            }
            if (CollectionUtils.isEmpty(results)) {
                return executeResult;
            }
        } catch (Exception e){
            throw new DtLoaderException(String.format("Failed to execute hbase customization,%s", e.getMessage()), e);
        } finally {
            if (hbaseSourceDTO.getPoolConfig() == null || MapUtils.isNotEmpty(hbaseSourceDTO.getKerberosConfig())) {
                close(rs, table, connection);
            } else {
                close(rs, table, null);
            }
            HbaseClient.destroyProperty();
        }
        //?????????????????????
        for (Result result : results) {
            List<Cell> cells = result.listCells();
            if (CollectionUtils.isEmpty(cells)) {
                continue;
            }
            long timestamp = 0L;
            HashMap<String, Object> row = Maps.newHashMap();
            for (Cell cell : cells){
                row.put(ROWKEY, Bytes.toString(cell.getRowArray(), cell.getRowOffset(),cell.getRowLength()));
                String family = Bytes.toString(cell.getFamilyArray(), cell.getFamilyOffset(),cell.getFamilyLength());
                String qualifier = Bytes.toString(cell.getQualifierArray(), cell.getQualifierOffset(),cell.getQualifierLength());
                Object value;
                String familyQualifier = String.format(FAMILY_QUALIFIER, family, qualifier);
                if (MapUtils.isNotEmpty(hbaseQueryDTO.getColumnTypes()) && Objects.nonNull(hbaseQueryDTO.getColumnTypes().get(familyQualifier))) {
                    HbaseQueryDTO.ColumnType columnType = hbaseQueryDTO.getColumnTypes().get(familyQualifier);
                    value = convertColumnType(columnType, cell);
                } else {
                    value = Bytes.toString(cell.getValueArray(), cell.getValueOffset(),cell.getValueLength());
                }
                row.put(familyQualifier, value);
                //???????????????????????????
                if (cell.getTimestamp() > timestamp) {
                    timestamp = cell.getTimestamp();
                }
            }
            row.put(TIMESTAMP, timestamp);
            executeResult.add(row);
        }
        return executeResult;
    }

    /**
     * ?????? hbase ???????????????
     *
     * @param columnType ???????????????
     * @param cell       cell
     * @return ???????????????
     */
    private Object convertColumnType(HbaseQueryDTO.ColumnType columnType, Cell cell) {
        switch (columnType) {
            case INT:
                return Bytes.toInt(cell.getValueArray(), cell.getValueOffset(),cell.getValueLength());
            case LONG:
                return Bytes.toLong(cell.getValueArray(), cell.getValueOffset(),cell.getValueLength());
            case FLOAT:
                return Bytes.toFloat(cell.getValueArray(), cell.getValueOffset());
            case DOUBLE:
                return Bytes.toDouble(cell.getValueArray(), cell.getValueOffset());
            case BOOLEAN:
                return Bytes.toBoolean(cell.getValueArray());
            case HEX:
                return Bytes.toHex(cell.getValueArray(), cell.getValueOffset(),cell.getValueLength());
            case SHORT:
                return Bytes.toShort(cell.getValueArray(), cell.getValueOffset(),cell.getValueLength());
            case BIG_DECIMAL:
                return Bytes.toBigDecimal(cell.getValueArray(), cell.getValueOffset(),cell.getValueLength());
            default:
                return Bytes.toString(cell.getValueArray(), cell.getValueOffset(),cell.getValueLength());

        }
    }

    /**
     * ?????? common-loader ????????? FilterList ??? hbase ?????? FilterList
     *
     * @param loaderFilterList common-loader ????????? FilterList
     * @param hbaseFilterList  hbase ??? FilterList
     */
    public static void convertFilter(com.dtstack.dtcenter.loader.dto.filter.FilterList loaderFilterList, FilterList hbaseFilterList) {
        List<com.dtstack.dtcenter.loader.dto.filter.Filter> loaderFilters = loaderFilterList.getFilters();
        if (CollectionUtils.isEmpty(loaderFilters)) {
            return;
        }
        for (com.dtstack.dtcenter.loader.dto.filter.Filter filter : loaderFilters) {
            if (filter instanceof com.dtstack.dtcenter.loader.dto.filter.FilterList) {
                com.dtstack.dtcenter.loader.dto.filter.FilterList filterList = (com.dtstack.dtcenter.loader.dto.filter.FilterList) filter;
                FilterList filterNew = new FilterList(convertOp(filterList.getOperator()));
                convertFilter(filterList, filterNew);
                hbaseFilterList.addFilter(filterNew);
            } else {
                hbaseFilterList.addFilter(FilterType.get(filter));
            }
        }
    }

    /**
     * ?????? FilterList.Operator
     *
     * @param operator common-loader ?????? FilterList.Operator
     * @return hbase ??? FilterList.Operator
     */
    public static FilterList.Operator convertOp(com.dtstack.dtcenter.loader.dto.filter.FilterList.Operator operator) {
        if (operator.equals(com.dtstack.dtcenter.loader.dto.filter.FilterList.Operator.MUST_PASS_ONE)) {
            return FilterList.Operator.MUST_PASS_ONE;
        }
        return FilterList.Operator.MUST_PASS_ALL;
    }

    /**
     * ??????hbase????????????connection??????null????????????????????????????????????kerberos????????????????????????hbase??????
     * @param connection hbase??????
     * @param hbaseSourceDTO hbase???????????????
     */
    private static void closeConnection(Connection connection, HbaseSourceDTO hbaseSourceDTO) {
        if (connection != null && (hbaseSourceDTO.getPoolConfig() == null || MapUtils.isNotEmpty(hbaseSourceDTO.getKerberosConfig()))) {
            try {
                connection.close();
            } catch (IOException e) {
                log.error("hbase Close connection exception", e);
            }
        }
    }

    /**
     * ??????admin???table???resultScanner......
     * @param closeables ???????????????????????????
     */
    private void close(Closeable... closeables) {
        try {
            if (Objects.nonNull(closeables)) {
                for (Closeable closeable : closeables) {
                    if (Objects.nonNull(closeable)) {
                        closeable.close();
                    }
                }
            }
        } catch (Exception e) {
            throw new DtLoaderException(String.format("hbase can not close table error,%s", e.getMessage()), e);
        }
    }
}
