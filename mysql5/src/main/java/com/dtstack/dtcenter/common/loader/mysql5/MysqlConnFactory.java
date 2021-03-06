/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtstack.dtcenter.common.loader.mysql5;

import com.dtstack.dtcenter.common.loader.rdbms.ConnFactory;
import com.dtstack.dtcenter.loader.dto.source.RdbmsSourceDTO;
import com.dtstack.dtcenter.loader.source.DataBaseType;
import org.apache.commons.lang3.StringUtils;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 17:11 2020/1/3
 * @Description：Mysql 连接
 */
public class MysqlConnFactory extends ConnFactory {
    public MysqlConnFactory() {
        driverName = DataBaseType.MySql.getDriverClassName();
        this.errorPattern = new MysqlErrorPattern();
    }

    @Override
    protected String dealSourceUrl(RdbmsSourceDTO rdbmsSourceDTO) {
        String schema = rdbmsSourceDTO.getSchema();
        String url = rdbmsSourceDTO.getUrl();
        if (StringUtils.isNotEmpty(schema)){
            String[] urlAyy = url.split("/");
            if (urlAyy.length > 2){
                url = urlAyy[0] + "//" + urlAyy[2] + "/" +schema;
            }
        }
        return url;
    }
}
