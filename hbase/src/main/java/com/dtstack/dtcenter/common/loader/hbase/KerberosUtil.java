package com.dtstack.dtcenter.common.loader.hbase;

import com.dtstack.dtcenter.common.exception.DtCenterDefException;
import com.dtstack.dtcenter.common.hadoop.DtKerberosUtils;
import com.dtstack.dtcenter.common.hadoop.HadoopConfTool;
import com.dtstack.dtcenter.common.kerberos.KerberosConfigVerify;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import sun.security.krb5.Config;

import java.util.Map;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 09:58 2020/8/4
 * @Description：Kerberos 工具类
 */
@Slf4j
public class KerberosUtil {
    public static synchronized UserGroupInformation loginKerberosWithUGI(Map<String, Object> confMap) {
        return loginKerberosWithUGI(confMap, "principal", "principalFile", "java.security.krb5.conf");
    }

    public static synchronized UserGroupInformation loginKerberosWithUGI(Map<String, Object> confMap, String principal, String keytab, String krb5Conf) {
        confMap = KerberosConfigVerify.replaceHost(confMap);
        principal = MapUtils.getString(confMap, principal);
        keytab = MapUtils.getString(confMap, keytab);
        krb5Conf = MapUtils.getString(confMap, krb5Conf);
        if (StringUtils.isNotEmpty(keytab) && !keytab.contains("/")) {
            keytab = MapUtils.getString(confMap, "keytabPath");
        }

        Preconditions.checkState(StringUtils.isNotEmpty(keytab), "keytab can not be empty");
        log.info("login kerberos, principal={}, path={}, krb5Conf={}", new Object[]{principal, keytab, krb5Conf});
        Configuration config = DtKerberosUtils.getConfig(confMap);
        if (StringUtils.isEmpty(principal) && StringUtils.isNotEmpty(keytab)) {
            principal = DtKerberosUtils.getPrincipal(keytab);
        }

        // 手动替换 Principal 参数，临时方案
        String hbaseMasterPrincipal = MapUtils.getString(confMap, HadoopConfTool.KEY_HBASE_MASTER_KERBEROS_PRINCIPAL);
        if (StringUtils.isNotBlank(hbaseMasterPrincipal) && StringUtils.isNotBlank(principal)) {
            int hbaseMasterPrincipalLos = hbaseMasterPrincipal.indexOf("/");
            int principalLos = principal.indexOf("/");
            principal = principal.replaceFirst(principal.substring(0, principalLos), hbaseMasterPrincipal.substring(0, hbaseMasterPrincipalLos));
        }

        if (MapUtils.isEmpty(confMap) || StringUtils.isEmpty(principal) || StringUtils.isEmpty(keytab)) {
            throw new DtCenterDefException("Kerberos Login fail, confMap or principal or keytab is null");
        }

        try {
            Config.refresh();
            if (StringUtils.isNotEmpty(krb5Conf)) {
                System.setProperty("java.security.krb5.conf", krb5Conf);
            }

            config.set("hadoop.security.authentication", "Kerberos");
            UserGroupInformation.setConfiguration(config);
            log.info("login kerberos, currentUser={}", new Object[]{UserGroupInformation.getCurrentUser(), principal, keytab, krb5Conf});
            return UserGroupInformation.loginUserFromKeytabAndReturnUGI(principal, keytab);
        } catch (Exception var6) {
            log.error("Login fail with config:{} \n {}", confMap, var6);
            throw new DtCenterDefException("Kerberos Login fail", var6);
        }
    }
}