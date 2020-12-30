package com.dtstack.dtcenter.common.loader.ftp;

import com.dtstack.dtcenter.common.loader.common.utils.AddressUtil;
import com.dtstack.dtcenter.loader.IDownloader;
import com.dtstack.dtcenter.loader.client.IClient;
import com.dtstack.dtcenter.loader.dto.ColumnMetaDTO;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.dtstack.dtcenter.loader.dto.Table;
import com.dtstack.dtcenter.loader.dto.source.FtpSourceDTO;
import com.dtstack.dtcenter.loader.dto.source.ISourceDTO;
import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import com.jcraft.jsch.JSchException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 22:50 2020/2/27
 * @Description：FTP 抽象类
 */
@Slf4j
public abstract class AbsFtpClient<T> implements IClient<T> {
    private static final int TIMEOUT = 60000;

    @Override
    public Boolean testCon(ISourceDTO iSource) {
        FtpSourceDTO ftpSourceDTO = (FtpSourceDTO) iSource;
        if (ftpSourceDTO == null || !AddressUtil.telnet(ftpSourceDTO.getUrl(), Integer.valueOf(ftpSourceDTO.getHostPort()))) {
            return Boolean.FALSE;
        }

        if (ftpSourceDTO.getProtocol() != null && "sftp".equalsIgnoreCase(ftpSourceDTO.getProtocol())) {
            SFTPHandler instance = null;
            try {
                Integer finalPort = Integer.valueOf(ftpSourceDTO.getHostPort());
                instance = SFTPHandler.getInstance(
                        new HashMap<String, String>() {{
                            put(SFTPHandler.KEY_HOST, ftpSourceDTO.getUrl());
                            put(SFTPHandler.KEY_PORT, String.valueOf(finalPort));
                            put(SFTPHandler.KEY_USERNAME, ftpSourceDTO.getUsername());
                            put(SFTPHandler.KEY_PASSWORD, ftpSourceDTO.getPassword());
                            put(SFTPHandler.KEY_TIMEOUT, String.valueOf(TIMEOUT));
                            put(SFTPHandler.KEY_AUTHENTICATION, Optional.ofNullable(ftpSourceDTO.getAuth()).orElse(""));
                            put(SFTPHandler.KEY_RSA, Optional.ofNullable(ftpSourceDTO.getPath()).orElse(""));
                        }});
            } catch (JSchException e) {
                log.error("与ftp服务器建立连接失败,请检查用户名和密码是否正确: host = {}, port : {}, username = {}, rsaPath = {}", ftpSourceDTO.getUrl(), ftpSourceDTO.getHostPort(), ftpSourceDTO.getUsername(), ftpSourceDTO.getPath());
                throw new DtLoaderException("与ftp服务器建立连接失败,请检查用户名和密码是否正确");
            } finally {
                if (instance != null) {
                    instance.close();
                }
            }
        } else {
            if (StringUtils.isBlank(ftpSourceDTO.getHostPort())) {
                ftpSourceDTO.setHostPort("21");
            }
            FTPClient ftpClient = new FTPClient();
            try {
                ftpClient.connect(ftpSourceDTO.getUrl(), Integer.valueOf(ftpSourceDTO.getHostPort()));
                ftpClient.login(ftpSourceDTO.getUsername(), ftpSourceDTO.getPassword());
                ftpClient.setConnectTimeout(TIMEOUT);
                ftpClient.setDataTimeout(TIMEOUT);
                if ("PASV".equalsIgnoreCase(ftpSourceDTO.getConnectMode())) {
                    ftpClient.enterRemotePassiveMode();
                    ftpClient.enterLocalPassiveMode();
                } else if ("PORT".equals(ftpSourceDTO.getConnectMode())) {
                    ftpClient.enterLocalActiveMode();
                }
                int reply = ftpClient.getReplyCode();
                if (!FTPReply.isPositiveCompletion(reply)) {
                    ftpClient.disconnect();
                    log.error("与ftp服务器建立连接失败,请检查用户名和密码是否正确: host = {}, port : {}, username = {}", ftpSourceDTO.getUrl(), ftpSourceDTO.getHostPort(), ftpSourceDTO.getUsername());
                    throw new DtLoaderException("与ftp服务器建立连接失败,请检查用户名和密码是否正确");
                }

                if (ftpClient != null) {
                    ftpClient.disconnect();
                }
            } catch (Exception e) {
                log.error("与ftp服务器建立连接失败,请检查用户名和密码是否正确: host = {}, port : {}, username = {}", ftpSourceDTO.getUrl(), ftpSourceDTO.getHostPort(), ftpSourceDTO.getUsername());
                throw new DtLoaderException("与ftp服务器建立连接失败,请检查用户名和密码是否正确");
            }
        }

        return true;
    }

    /********************************* FTP 数据库无需实现的方法 ******************************************/
    @Override
    public Connection getCon(ISourceDTO iSource) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public List<Map<String, Object>> executeQuery(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public Boolean executeSqlWithoutResultSet(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public List<String> getTableList(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public List<String> getTableListBySchema(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public List<String> getColumnClassInfo(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public List<ColumnMetaDTO> getColumnMetaData(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public List<ColumnMetaDTO> getColumnMetaDataWithSql(ISourceDTO source, SqlQueryDTO queryDTO) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public List<ColumnMetaDTO> getFlinkColumnMetaData(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public String getTableMetaComment(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public List<List<Object>> getPreview(ISourceDTO iSource, SqlQueryDTO queryDTO) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public IDownloader getDownloader(ISourceDTO source, SqlQueryDTO queryDTO) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public List<String> getAllDatabases(ISourceDTO source, SqlQueryDTO queryDTO) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public String getCreateTableSql(ISourceDTO source, SqlQueryDTO queryDTO) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public List<ColumnMetaDTO> getPartitionColumn(ISourceDTO source, SqlQueryDTO queryDTO) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public Table getTable(ISourceDTO source, SqlQueryDTO queryDTO) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public String getCurrentDatabase(ISourceDTO source) {
        throw new DtLoaderException("Not Support");
    }

    @Override
    public Boolean createDatabase(ISourceDTO source, String dbName, String comment) {
        throw new DtLoaderException("ftp数据源不支持该方法");
    }

    @Override
    public Boolean isDatabaseExists(ISourceDTO source, String dbName) {
        throw new DtLoaderException("ftp数据源不支持该方法");
    }

    @Override
    public Boolean isTableExistsInDatabase(ISourceDTO source, String tableName, String dbName) {
        throw new DtLoaderException("ftp数据源不支持该方法");
    }
}