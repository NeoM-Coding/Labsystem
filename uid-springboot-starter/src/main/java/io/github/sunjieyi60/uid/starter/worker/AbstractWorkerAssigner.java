package io.github.sunjieyi60.uid.starter.worker;

import io.github.sunjieyi60.uid.starter.constant.WorkerNodeType;
import io.github.sunjieyi60.uid.starter.utils.DockerUtils;
import io.github.sunjieyi60.uid.starter.utils.NetUtils;
import io.github.sunjieyi60.uid.starter.worker.entity.WorkerNodeEntity;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;

/**
 * @author DengJun 2021/5/6
 */
public abstract class AbstractWorkerAssigner {
    @Value("${server.port}")
    private String applicationPort;

    /**
     * Build worker node entity by IP and PORT
     */
    public WorkerNodeEntity buildWorkerNode() {
        WorkerNodeEntity workerNodeEntity = new WorkerNodeEntity();
        if (DockerUtils.isDocker()) {
            workerNodeEntity.setType(WorkerNodeType.CONTAINER.value());
            workerNodeEntity.setHostName(DockerUtils.getDockerHost());
            workerNodeEntity.setPort(DockerUtils.getDockerPort());
        } else {
            workerNodeEntity.setType(WorkerNodeType.ACTUAL.value());
            // 域名不使用服务名，否则多实例环境时节点ID会相同
            workerNodeEntity.setHostName(NetUtils.getLocalAddress());
            String port = StringUtils.isBlank(applicationPort) ?
                    System.currentTimeMillis() + "-" + RandomUtils.nextInt(100000) : applicationPort;
            workerNodeEntity.setPort(port);
        }
        return workerNodeEntity;
    }
}
