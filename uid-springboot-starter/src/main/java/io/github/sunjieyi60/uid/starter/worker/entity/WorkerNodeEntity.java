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
package io.github.sunjieyi60.uid.starter.worker.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Entity for M_WORKER_NODE
 *
 * @author yutianbao
 */
@Data
@Entity
@Table(name = "tf_ap_worker_node")
public class WorkerNodeEntity implements Serializable {

    /**
     * Entity unique id (table unique)
     */
    @Id
    @Column(name = "id", unique = true, columnDefinition = "bigint NOT NULL AUTO_INCREMENT COMMENT 'auto increment id'")
    private Long id;

    /**
     * Type of CONTAINER: HostName, ACTUAL : IP.
     */
    @Column(name = "host_name", columnDefinition = "VARCHAR(64) NOT NULL COMMENT 'host name'")
    private String hostName;

    /**
     * Type of CONTAINER: Port, ACTUAL : Timestamp + Random(0-10000)
     */
    @Column(name = "port", columnDefinition = "VARCHAR(64) NOT NULL COMMENT 'port'")
    private String port;

    @Column(name = "type", columnDefinition = "TINYINT(2) NOT NULL COMMENT 'node type: ACTUAL or CONTAINER'")
    private int type;

    /**
     * Worker launch date, default now
     */
    @Column(name = "launch_date", columnDefinition = "DATETIME NOT NULL COMMENT 'launch date'")
    private Date launchDate = new Date();

    /**
     * Created time
     */
    @Column(name = "created", columnDefinition = "DATETIME NOT NULL COMMENT 'created time'")
    private Date created;

    /**
     * Last modified
     */
    @Column(name = "modified", columnDefinition = "DATETIME NOT NULL COMMENT 'modified time'")
    private Date modified;
}
