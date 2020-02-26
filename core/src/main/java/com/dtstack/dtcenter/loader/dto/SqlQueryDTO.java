package com.dtstack.dtcenter.loader.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 14:40 2020/2/26
 * @Description：查询信息
 */
@Data
@Builder
public class SqlQueryDTO {
    /**
     * 查询 SQL
     */
    private String sql;

    /**
     * 表名称
     */
    private String tableName;

    /**
     * 表名称(正则)
     */
    private String tableNamePattern;

    /**
     * 模式即 DBName
     */
    private String schema;

    /**
     * 模式即 DBName(正则)
     */
    private String schemaPattern;

    /**
     * 表类型
     * {@link java.sql.DatabaseMetaData#getTableTypes()}
     */
    private String[] tableTypes;

    /**
     * 字段名称
     */
    private List<String> columns;

    /**
     * 是否需要视图表，默认 false 不过滤
     */
    private Boolean view = false;

    /**
     * 是否过滤分区字段，默认 false 不过滤
     */
    private Boolean filterPartitionColumns = false;
}