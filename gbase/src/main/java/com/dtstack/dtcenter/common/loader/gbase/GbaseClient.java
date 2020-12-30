package com.dtstack.dtcenter.common.loader.gbase;

import com.dtstack.dtcenter.common.loader.common.DtClassConsistent;
import com.dtstack.dtcenter.common.loader.common.utils.DBUtil;
import com.dtstack.dtcenter.common.loader.rdbms.AbsRdbmsClient;
import com.dtstack.dtcenter.common.loader.rdbms.ConnFactory;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.dtstack.dtcenter.loader.dto.source.GBaseSourceDTO;
import com.dtstack.dtcenter.loader.dto.source.ISourceDTO;
import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import com.dtstack.dtcenter.loader.source.DataSourceType;

import java.sql.ResultSet;
import java.sql.Statement;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 17:57 2020/1/7
 * @Description：GBase8a 客户端
 */
public class GbaseClient extends AbsRdbmsClient {
    @Override
    protected ConnFactory getConnFactory() {
        return new GbaseConnFactory();
    }

    @Override
    protected DataSourceType getSourceType() {
        return DataSourceType.GBase_8a;
    }

    @Override
    public String getTableMetaComment(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        Integer clearStatus = beforeColumnQuery(iSource, queryDTO);
        GBaseSourceDTO gBaseSourceDTO = (GBaseSourceDTO) iSource;
        Statement statement = null;
        ResultSet resultSet = null;

        try {
            statement = gBaseSourceDTO.getConnection().createStatement();
            resultSet = statement.executeQuery("show table status");
            while (resultSet.next()) {
                String dbTableName = resultSet.getString(1);

                if (dbTableName.equalsIgnoreCase(queryDTO.getTableName())) {
                    return resultSet.getString(DtClassConsistent.PublicConsistent.COMMENT);
                }
            }
        } catch (Exception e) {
            throw new DtLoaderException(String.format("获取表:%s 的信息时失败. 请联系 DBA 核查该库、表信息.",
                    queryDTO.getTableName()), e);
        } finally {
            DBUtil.closeDBResources(resultSet, statement, gBaseSourceDTO.clearAfterGetConnection(clearStatus));
        }
        return null;
    }
}
