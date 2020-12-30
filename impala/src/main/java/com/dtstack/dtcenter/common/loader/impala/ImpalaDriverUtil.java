package com.dtstack.dtcenter.common.loader.impala;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 16:18 2020/12/23
 * @Description：Impala 工具类
 */
@Slf4j
public class ImpalaDriverUtil {
    /**
     * 设置 Schema 信息
     *
     * @param conn
     * @param url
     * @return
     */
    public static Connection setSchema(Connection conn, String schema) {
        if (StringUtils.isBlank(schema)) {
            return conn;
        }

        try {
            conn.setSchema(schema);
        } catch (SQLException e) {
            log.error("Hive 设置 Schema 异常 : ", e.getMessage(), e);
        }
        return conn;
    }
}