package com.dtstack.dtcenter.common.loader.db2;

import com.dtstack.dtcenter.common.exception.DtCenterDefException;
import com.dtstack.dtcenter.loader.downloader.IDownloader;
import com.dtstack.dtcenter.loader.dto.Column;
import com.dtstack.dtcenter.loader.utils.SqlFormatUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @company: www.dts
 * tack.com
 * @Author ：wangchuan
 * @Date ：Created in 下午4:44 2020/5/29
 * @Description：db2表下载
 */

public class Db2Downloader implements IDownloader {

    private int pageNum;

    private int pageAll;

    private List<Column> columnNames;

    private int totalLine;

    private Connection connection;

    private String sql;

    private String schema;

    private Statement statement;

    private int pageSize;

    private int columnCount;

    public Db2Downloader(Connection connection, String sql, String schema) {
        this.connection = connection;
        this.sql = SqlFormatUtil.formatSql(sql);
        this.schema = schema;
    }

    @Override
    public boolean configure() throws Exception {
        if (null == connection || StringUtils.isEmpty(sql)) {
            throw new DtCenterDefException("文件不存在");
        }
        totalLine = 0;
        pageSize = 100;
        pageNum = 1;
        statement = connection.createStatement();

        String countSQL = String.format("SELECT COUNT(*) FROM (%s) temp", sql);
        String showColumns = String.format("SELECT * FROM (%s) t limit 1", sql);

        ResultSet totalResultSet = null;
        ResultSet columnsResultSet = null;
        try {
            totalResultSet = statement.executeQuery(countSQL);
            while (totalResultSet.next()) {
                //获取总行数
                totalLine = totalResultSet.getInt(1);
            }
            columnsResultSet = statement.executeQuery(showColumns);
            //获取列信息
            columnNames = new ArrayList<>();
            columnCount = columnsResultSet.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                Column column = new Column();
                column.setName(columnsResultSet.getMetaData().getColumnName(i));
                column.setType(columnsResultSet.getMetaData().getColumnTypeName(i));
                column.setIndex(i);
                columnNames.add(column);
            }
            //获取总页数
            pageAll = (int) Math.ceil(totalLine / (double) pageSize);
        } catch (Exception e) {
            throw new DtCenterDefException("构造 DB2 下载器信息异常 : " + e.getMessage(), e);
        } finally {
            if (totalResultSet != null) {
                totalResultSet.close();
            }
            if (columnsResultSet != null) {
                columnsResultSet.close();
            }
        }
        return true;
    }

    @Override
    public List<String> getMetaInfo() throws Exception {
        if (CollectionUtils.isNotEmpty(columnNames)) {
            return columnNames.stream().map(Column::getName).collect(Collectors.toList());
        }
        return null;
    }

    @Override
    public List<List<String>> readNext() throws Exception {
        //分页查询，一次一百条
        String limitSQL = String.format("SELECT * FROM (%s) t limit %s,%s", sql, pageSize * (pageNum - 1), pageSize);
        List<List<String>> pageTemp = new ArrayList<>(100);
        try (ResultSet resultSet = statement.executeQuery(limitSQL)) {
            while (resultSet.next()) {
                List<String> columns = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(resultSet.getString(i));
                }
                pageTemp.add(columns);
            }
        } catch (Exception e) {
            throw new DtCenterDefException("读取 Db2 信息异常 : " + e.getMessage(), e);
        }

        pageNum++;
        return pageTemp;
    }

    @Override
    public boolean reachedEnd() throws Exception {
        return pageAll < pageNum;
    }

    @Override
    public boolean close() throws Exception {
        statement.close();
        connection.close();
        return true;
    }

    @Override
    public String getFileName() {
        return null;
    }
}
