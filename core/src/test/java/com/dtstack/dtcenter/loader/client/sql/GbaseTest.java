package com.dtstack.dtcenter.loader.client.sql;

import com.dtstack.dtcenter.loader.client.AbsClientCache;
import com.dtstack.dtcenter.loader.client.IClient;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.dtstack.dtcenter.loader.dto.source.GBaseSourceDTO;
import com.dtstack.dtcenter.loader.enums.ClientType;
import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import com.dtstack.dtcenter.loader.source.DataSourceType;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 11:06 2020/4/16
 * @Description：gbase 8a 测试
 */
public class GbaseTest {
    private static final AbsClientCache clientCache = ClientType.DATA_SOURCE_CLIENT.getClientCache();
    private static GBaseSourceDTO source = GBaseSourceDTO.builder()
            .url("jdbc:gbase://172.16.8.193:5258/dtstack")
            .username("root")
            .password("123456")
            .build();

    @BeforeClass
    public static void beforeClass() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.GBase_8a.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().sql("drop table if exists nanqi").build();
        client.executeSqlWithoutResultSet(source, queryDTO);
        queryDTO = SqlQueryDTO.builder().sql("create table nanqi (id int COMMENT 'ID', name varchar(50) COMMENT '名称') comment 'table comment'").build();
        client.executeSqlWithoutResultSet(source, queryDTO);
        queryDTO = SqlQueryDTO.builder().sql("insert into nanqi values (1, 'nanqi')").build();
        client.executeSqlWithoutResultSet(source, queryDTO);
    }

    @Test
    public void getConnFactory() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.GBase_8a.getPluginName());
        Boolean isConnected = client.testCon(source);
        if (!isConnected) {
            throw new DtLoaderException("数据源连接异常");
        }
    }

    @Test
    public void getTableList() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.GBase_8a.getPluginName());
        List tableList = client.getTableList(source, null);
        assert tableList != null;
    }

    @Test
    public void executeSqlWithoutResultSet() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.GBase_8a.getPluginName());
        String createSQL = "show tables";
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().sql(createSQL).build();
        client.executeSqlWithoutResultSet(source, queryDTO);
    }

    @Test
    public void getTableMetaComment() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.GBase_8a.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("nanqi").build();
        String metaComment = client.getTableMetaComment(source, queryDTO);
        Assert.assertEquals("table comment", metaComment);
    }
}
