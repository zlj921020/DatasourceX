package com.dtstack.dtcenter.common.loader.oracle;

import com.dtstack.dtcenter.common.exception.DtCenterDefException;
import com.dtstack.dtcenter.loader.IDownloader;
import com.dtstack.sql.Column;
import com.dtstack.sql.utils.SqlFormatUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @company: www.dtstack.com
 * @Author ：wangchuan
 * @Date ：Created in 上午10:15 2020/5/29
 * @Description：oracle表下载
 */
public class OracleDownloader implements IDownloader {

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

    public OracleDownloader(Connection connection, String sql, String schema) {
        this.connection = connection;
        this.sql = SqlFormatUtil.formatSql(sql);
        this.schema = schema;
    }

    @Override
    public void configure() throws Exception {
        if (null == connection || StringUtils.isEmpty(sql)) {
            throw new DtCenterDefException("文件不存在");
        }
        totalLine = 0;
        pageSize = 100;
        pageNum = 1;
        statement = connection.createStatement();
        if (StringUtils.isNotEmpty(schema)) {
            //选择schema
            String useSchema = String.format("USE %s", schema);
            statement.execute(useSchema);
        }
        String countSQL = String.format("SELECT COUNT(*) FROM (%s) temp", sql);
        String showColumns = String.format("SELECT * FROM (%s) t where rownum<=1", sql);

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
            throw new DtCenterDefException("构造 Oracle 下载器信息异常 : " + e.getMessage(), e);
        } finally {
            columnsResultSet.close();
            totalResultSet.close();
        }
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
        String limitSQL = String.format("SELECT * FROM (SELECT a.*, ROWNUM rn FROM (SELECT * FROM (%s)) a WHERE " +
                "ROWNUM <= %s) WHERE rn > %s", sql, pageSize * pageNum, pageSize * (pageNum - 1));
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
            throw new DtCenterDefException("读取 Oracle 信息异常 : " + e.getMessage(), e);
        }

        pageNum++;
        return pageTemp;
    }

    @Override
    public boolean reachedEnd() throws Exception {
        return pageAll < pageNum;
    }

    @Override
    public void close() throws Exception {
        statement.close();
        connection.close();
    }

    @Override
    public String getFileName() {
        return null;
    }
}