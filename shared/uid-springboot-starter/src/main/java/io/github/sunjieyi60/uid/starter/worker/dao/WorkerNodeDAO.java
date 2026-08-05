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
package io.github.sunjieyi60.uid.starter.worker.dao;

import io.github.sunjieyi60.uid.starter.worker.entity.WorkerNodeEntity;
import org.apache.ibatis.annotations.Param;

/**
 * DAO for M_WORKER_NODE
 *
 * @author yutianbao
 */
public interface WorkerNodeDAO {
    /**
     * Get {@link WorkerNodeEntity} by node host
     *
     * @param hostName host
     * @param port     port
     * @return WorkerNodeEntity
     */
    WorkerNodeEntity getWorkerNodeByHostPort(@Param("hostName") String hostName, @Param("port") String port);

    /**
     * Add {@link WorkerNodeEntity}
     *
     * @param workerNodeEntity worker
     * @return Id
     */
    int addWorkerNode(WorkerNodeEntity workerNodeEntity);

    /**
     * update {@link WorkerNodeEntity}
     *
     * @param workerNodeEntity worker
     * @return Id
     */
    int updateWorkerNode(WorkerNodeEntity workerNodeEntity);

    /**
     * 查询数据库是否存在
     */
    int queryDatabaseExist();

    /**
     * 查询数据表是否存在
     */
    int queryTableExist();

    /**
     * 创建表
     */
    int createTable();

    /**
     * 创建库
     */
    int createDatabase();
}
