package com.dtstack.dtcenter.loader.utils;

import com.dtstack.dtcenter.common.exception.DBErrorCode;
import com.dtstack.dtcenter.common.exception.DtCenterDefException;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 11:13 2020/1/13
 * @Description：数据库工具类
 */
public class DBUtil {
    private static final Logger LOG = LoggerFactory.getLogger(DBUtil.class);

    /**
     * 根据 SQL 查询
     *
     * @param conn
     * @param sql
     * @param closeConn 是否关闭连接
     * @return
     * @throws Exception
     */
    public static List<Map<String, Object>> executeQuery(Connection conn, String sql, Boolean closeConn) throws Exception {
        List<Map<String, Object>> result = Lists.newArrayList();
        ResultSet res = null;
        Statement statement = null;
        try {
            statement = conn.createStatement();
            res = statement.executeQuery(sql);
            int columns = res.getMetaData().getColumnCount();
            List<String> columnName = Lists.newArrayList();
            for (int i = 0; i < columns; i++) {
                columnName.add(res.getMetaData().getColumnName(i + 1));
            }

            while (res.next()) {
                Map<String, Object> row = Maps.newLinkedHashMap();
                for (int i = 0; i < columns; i++) {
                    row.put(columnName.get(i), res.getObject(i + 1));
                }
                result.add(row);
            }
        } catch (Exception e) {
            throw new DtCenterDefException(DBErrorCode.SQL_EXE_EXCEPTION, e);
        } finally {
            DBUtil.closeDBResources(res, statement, closeConn ? conn : null);
        }
        return result;
    }

    /**
     * 执行查询，无需结果集
     *
     * @param conn
     * @param sql
     * @param closeConn 是否关闭连接
     * @return
     * @throws Exception
     */
    public static void executeSqlWithoutResultSet(Connection conn, String sql, Boolean closeConn) throws Exception {
        Statement statement = null;
        try {
            statement = conn.createStatement();
            statement.executeQuery(sql);
        } catch (Exception e) {
            throw new DtCenterDefException(DBErrorCode.SQL_EXE_EXCEPTION, e);
        } finally {
            DBUtil.closeDBResources(null, statement, closeConn ? conn : null);
        }
    }

    /**
     * 重置表类型
     * {@link java.sql.DatabaseMetaData#getTableTypes()}
     *
     * @param queryDTO
     * @return
     */
    public static String[] getTableTypes(SqlQueryDTO queryDTO) {
        if (ArrayUtils.isNotEmpty(queryDTO.getTableTypes())) {
            return queryDTO.getTableTypes();
        }

        String[] types = new String[BooleanUtils.isTrue(queryDTO.getView()) ? 2 : 1];
        types[0] = "TABLE";
        if (BooleanUtils.isTrue(queryDTO.getView())) {
            types[1] = "VIEW";
        }
        return types;
    }

    public static void closeDBResources(ResultSet rs, Statement stmt, Connection conn) {
        try {
            if (null != rs) {
                rs.close();
            }

            if (null != stmt) {
                stmt.close();
            }

            if (null != conn) {
                conn.close();
            }
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
        }
    }
}