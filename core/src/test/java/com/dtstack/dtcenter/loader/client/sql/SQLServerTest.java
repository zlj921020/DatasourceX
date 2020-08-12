package com.dtstack.dtcenter.loader.client.sql;

import com.dtstack.dtcenter.common.exception.DtCenterDefException;
import com.dtstack.dtcenter.loader.downloader.IDownloader;
import com.dtstack.dtcenter.loader.client.AbsClientCache;
import com.dtstack.dtcenter.loader.client.IClient;
import com.dtstack.dtcenter.loader.dto.ColumnMetaDTO;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.dtstack.dtcenter.loader.dto.source.SqlserverSourceDTO;
import com.dtstack.dtcenter.loader.enums.ClientType;
import com.dtstack.dtcenter.loader.source.DataSourceType;
import org.junit.Test;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 04:10 2020/2/29
 * @Description：SQLServer 测试
 */
public class SQLServerTest {
    private static final AbsClientCache clientCache = ClientType.DATA_SOURCE_CLIENT.getClientCache();

    SqlserverSourceDTO source = SqlserverSourceDTO.builder()
            .url("jdbc:jtds:sqlserver://172.16.8.190:1401;DatabaseName=cc")
            .username("sa")
            .password("<admin123ADMIN!>")
            .build();

    @Test
    public void getCon() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.SQLServer.getPluginName());
        Connection con = client.getCon(source);
        con.createStatement().close();
        con.close();
    }

    @Test
    public void testCon() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.SQLServer.getPluginName());
        Boolean isConnected = client.testCon(source);
        if (Boolean.FALSE.equals(isConnected)) {
            throw new DtCenterDefException("连接异常");
        }
    }

    @Test
    public void executeQuery() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.SQLServer.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().sql("select 1111").build();
        List<Map<String, Object>> mapList = client.executeQuery(source, queryDTO);
        System.out.println(mapList.size());
    }

    @Test
    public void executeSqlWithoutResultSet() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.SQLServer.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().sql("select 1111").build();
        client.executeSqlWithoutResultSet(source, queryDTO);
    }

    @Test
    public void getTableList() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.SQLServer.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().view(true).build();
        List<String> tableList = client.getTableList(source, queryDTO);
        System.out.println(tableList);
    }

    @Test
    public void getColumnClassInfo() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.SQLServer.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("jiangbo_dev_copy").build();
        List<String> columnClassInfo = client.getColumnClassInfo(source, queryDTO);
        System.out.println(columnClassInfo.size());
    }

    @Test
    public void getColumnMetaData() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.SQLServer.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("dbo.demo").build();
        List<ColumnMetaDTO> columnMetaData = client.getColumnMetaData(source, queryDTO);
        System.out.println(columnMetaData);
    }

    @Test
    public void getTableMetaComment() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.SQLServer.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("TempTable0").build();
        String metaComment = client.getTableMetaComment(source, queryDTO);
        System.out.println(metaComment);
    }

    @Test
    public void getPreview() throws Exception{
        IClient client = clientCache.getClient(DataSourceType.SQLServer.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().previewNum(20).tableName("dbo.[dd.dd]").build();
        List preview = client.getPreview(source, queryDTO);
        System.out.println(preview);
    }

    @Test
    public void getTableListWithSchema() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.SQLServer.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().sql("" +
                "select sys.objects.name tableName,sys.schemas.name schemaName from sys.objects,sys.schemas where sys.objects.type='U'  and sys.objects.schema_id=sys.schemas.schema_id").build();
        List list = client.executeQuery(source, queryDTO);
        System.out.println(list);
    }

    @Test
    public void testGetDownloader() throws Exception {
        IClient client = clientCache.getClient(DataSourceType.SQLServer.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().sql("select * from jiangbo_dev_copy").build();
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
}
