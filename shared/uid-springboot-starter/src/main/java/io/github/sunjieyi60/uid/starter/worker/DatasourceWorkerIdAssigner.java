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
package io.github.sunjieyi60.uid.starter.worker;

import io.github.sunjieyi60.uid.starter.exception.UidGenerateException;
import io.github.sunjieyi60.uid.starter.worker.dao.WorkerNodeDAO;
import io.github.sunjieyi60.uid.starter.worker.entity.WorkerNodeEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Represents an implementation of {@link WorkerIdAssigner},
 * the worker id will be discarded after assigned to the UidGenerator
 * 基于数据库获取 workerId（机器节点ID）
 *
 * <p>采用 MyBatis 原生编程式事务管理（手动 commit/rollback），
 * 不依赖 Spring 声明式事务，确保在独立数据源场景下事务边界清晰可控。</p>
 *
 * @author yutianbao
 */
@Slf4j
@RequiredArgsConstructor
public class DatasourceWorkerIdAssigner extends AbstractWorkerAssigner implements WorkerIdAssigner {
    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Assign worker id base on database.<p>
     * If there is host name & port in the environment, we considered that the node runs in Docker container<br>
     * Otherwise, the node runs on an actual machine.
     *
     * @return assigned worker id
     */
    @Override
    public long assignWorkerId() {
        // build worker node entity
        WorkerNodeEntity nodeEntity = buildWorkerNode();

        SqlSession session = sqlSessionFactory.openSession(false);
        try {
            WorkerNodeDAO nodeDAO = session.getMapper(WorkerNodeDAO.class);
            doCheck(nodeDAO);
            WorkerNodeEntity node = nodeDAO.getWorkerNodeByHostPort(nodeEntity.getHostName(), nodeEntity.getPort());
            long workerId;
            if (node == null) {
                nodeDAO.addWorkerNode(nodeEntity);
                workerId = nodeEntity.getId();
                log.info("Add worker node:" + nodeEntity);
            } else {
                nodeDAO.updateWorkerNode(nodeEntity);
                log.info("Update worker node:" + nodeEntity);
                workerId = node.getId();
            }
            session.commit();
            return workerId;
        } catch (Exception e) {
            session.rollback();
            log.error("Assign worker id failed, transaction rollback", e);
            throw new UidGenerateException("Assign worker id failed: " + e.getMessage(), e);
        } finally {
            session.close();
        }
    }

    // 检查当前 uid 数据源中的 worker 表，数据库本身由独立数据源配置负责选择。
    public void doCheck(WorkerNodeDAO nodeDAO) {
        if (nodeDAO.queryTableExist() == 0 && nodeDAO.createTable() > 0) {
            log.info("Not found table 'tf_ap_worker_node',auto created success");
        }
    }
}
