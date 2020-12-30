package com.dtstack.dtcenter.loader.dto.source;

import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import com.dtstack.dtcenter.loader.source.DataSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.sql.Connection;
import java.util.Map;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 22:18 2020/11/23
 * @Description：WebSocket 数据源
 */
@Data
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketSourceDTO implements ISourceDTO {
    /**
     * 用户名
     */
    protected String username;

    /**
     * 密码
     */
    protected String password;

    /**
     * 数据源类型
     */
    @Builder.Default
    protected Integer sourceType = DataSourceType.WEB_SOCKET.getVal();

    /**
     * 地址
     */
    protected String url;

    /**
     * 鉴权参数
     */
    protected Map<String, String> authParams;

    @Override
    public Connection getConnection() {
        throw new DtLoaderException("不支持该方法");
    }

    @Override
    public void setConnection(Connection connection) {
        throw new DtLoaderException("不支持该方法");
    }
}
