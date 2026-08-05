/*
 * Copyright (c) 2017 Baidu, Inc. All Rights Reserve.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.sunjieyi60.uid.starter;

import io.github.sunjieyi60.uid.starter.component.CachedUidGenerator;
import io.github.sunjieyi60.uid.starter.exception.UidGenerateException;
import io.github.sunjieyi60.uid.starter.worker.DatasourceWorkerIdAssigner;
import io.github.sunjieyi60.uid.starter.worker.RedisWorkerIdAssigner;

/**
 * Represents a unique id generator.
 * 考虑到以下两种情况：
 * 1、所有服务使用的是同一台数据库服务器
 * 2、各服务使用的是独立的数据库
 * 解决方案：
 * 情况①在主库中直接创建下表即可 {@link DatasourceWorkerIdAssigner}
 * 情况②若有必要可另外开发一个发号服务，所有的服务通过发号服务获取ID。
 * 还可以使用分布式缓存来生成每台机器的工作节点 {@link RedisWorkerIdAssigner}
 * 此外，使用当前Starter甚至可以不引入ORM依赖或Redis使用内存方式的环形数组 {@link CachedUidGenerator}
 * //======================== 服务的工作节点登记表(MyBatis环境自动创建) =========================//
 * CREATE TABLE `tf_ap_worker_node` (
 * `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'auto increment id',
 * `created` datetime NOT NULL COMMENT 'created time',
 * `host_name` varchar(64) NOT NULL COMMENT 'host name',
 * `launch_date` datetime NOT NULL COMMENT 'launch date',
 * `modified` datetime NOT NULL COMMENT 'modified time',
 * `port` varchar(64) NOT NULL COMMENT 'port',
 * `type` tinyint(2) NOT NULL COMMENT 'node type: ACTUAL or CONTAINER',
 * PRIMARY KEY (`id`)
 * ) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4;
 *
 * @author yutianbao
 */
public interface UidGenerator {
    /**
     * 替换JPA中的默认IdentityGenerator的实现
     * 应用实例：
     * | @Id
     * | @GeneratedValue(strategy = GenerationType.AUTO, generator = "uid")
     * | @GenericGenerator(name = "uid", strategy = UidGenerator.JPA_ID)
     * | private Long id;
     */
    String JPA_ID = "io.github.sunjieyi60.uid.starter.component.JpaIdentityGenerator";

    /**
     * Get a unique ID
     *
     * @return UID
     * @throws UidGenerateException e
     */
    long getUID() throws UidGenerateException;

    /**
     * Parse the UID into elements which are used to generate the UID. <br>
     * Such as timestamp & workerId & sequence...
     *
     * @param uid long uid
     * @return Parsed info
     */
    String parseUID(long uid);
}
