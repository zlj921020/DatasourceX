package com.dtstack.dtcenter.loader.client.sql;

import com.dtstack.dtcenter.common.exception.DtCenterDefException;
import com.dtstack.dtcenter.loader.downloader.IDownloader;
import com.dtstack.dtcenter.loader.client.AbsClientCache;
import com.dtstack.dtcenter.loader.client.IClient;
import com.dtstack.dtcenter.loader.dto.ColumnMetaDTO;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.dtstack.dtcenter.loader.dto.source.Mysql5SourceDTO;
import com.dtstack.dtcenter.loader.enums.ClientType;
import com.dtstack.dtcenter.loader.source.DataSourceType;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 03:41 2020/2/29
 * @Description：MySQL 5 测试
 */
public class Mysql5Test {
    private static final AbsClientCache clientCache = ClientType.DATA_SOURCE_CLIENT.getClientCache();

    Mysql5SourceDTO source = Mysql5SourceDTO.builder()
            .url("jdbc:mysql://172.16.101.249:3306/streamapp")
            .username("drpeco")
            .password("DT@Stack#123")
            .schema("streamapp")
            .build();

    @Test
    public void getCon() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.MySQL.getPluginName());
        Connection con = client.getCon(source);
        con.createStatement().close();
        con.close();
    }

    @Test
    public void testCon() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.MySQL.getPluginName());
        Boolean isConnected = client.testCon(source);
        Assert.assertTrue(isConnected);
    }

    @Test(expected = DtCenterDefException.class)
    public void testErrorCon() {
        IClient client = clientCache.getClient(DataSourceType.MySQL.getPluginName());
        source.setUsername("nanqi233");
        client.testCon(source);
    }

    @Test
    public void executeQuery() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.MySQL.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().sql("show tables").build();
        List<Map<String, Object>> mapList = client.executeQuery(source, queryDTO);
        System.out.println(mapList.size());
    }

    @Test
    public void executeSqlWithoutResultSet() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.MySQL.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().sql("show tables").build();
        client.executeSqlWithoutResultSet(source, queryDTO);
    }

    @Test
    public void getTableList() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.MySQL.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().build();
        List<String> tableList = client.getTableList(source, queryDTO);
        System.out.println(tableList);
    }

    @Test
    public void getColumnClassInfo() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.MySQL.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("rdos_dict").build();
        List<String> columnClassInfo = client.getColumnClassInfo(source, queryDTO);
        System.out.println(columnClassInfo.size());
    }

    @Test
    public void getColumnMetaData() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.MySQL.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("rdos_dict").build();
        List<ColumnMetaDTO> columnMetaData = client.getColumnMetaData(source, queryDTO);
        System.out.println(columnMetaData.size());
    }

    @Test
    public void getTableMetaComment() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.MySQL.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("rdos_dict").build();
        String metaComment = client.getTableMetaComment(source, queryDTO);
        System.out.println(metaComment);
    }

    @Test
    public void testGetDownloader() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.MySQL.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().sql("select * from rdos_dict").build();
        IDownloader downloader = client.getDownloader(source, queryDTO);
        downloader.configure();
        List<String> metaInfo = downloader.getMetaInfo();
        System.out.println(metaInfo);
        while (!downloader.reachedEnd()){
            List<List<String>> o = (List<List<String>>)downloader.readNext();
            for (List<String> list:o){
                System.out.println(list);
            }
        }
    }

    /**
     * 数据预览测试
     * @throws Exception
     */
    @Test
    public void testGetPreview() throws Exception{
        IClient client = clientCache.getClient(DataSourceType.MySQL.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("rdos_dict").build();
        List preview = client.getPreview(source, queryDTO);
        System.out.println(preview);
    }

    /**
     * 根据自定义sql获取表字段信息
     */
    @Test
    public void getColumnMetaDataWithSql() throws Exception{
        IClient client = clientCache.getClient(DataSourceType.MySQL.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().sql("select a.*,b.role_name from rdos_dict a join rdos_role b on 1=1").build();
        List sql = client.getColumnMetaDataWithSql(source, queryDTO);
        System.out.println(sql);
    }

    @Test
    public void getAllDatabases() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.MySQL.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().build();
        System.out.println(client.getAllDatabases(source,queryDTO));
    }

    @Test
    public void getCreateTableSql() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.MySQL.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("rdos_dict").build();
        System.out.println(client.getCreateTableSql(source,queryDTO));
    }

    @Test
    public void getPartitionColumn() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.MySQL.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("rdos_dict").build();
        System.out.println(client.getPartitionColumn(source,queryDTO));
    }
}
