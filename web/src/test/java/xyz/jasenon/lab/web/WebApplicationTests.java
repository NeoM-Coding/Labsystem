package xyz.jasenon.lab.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import xyz.jasenon.lab.audit.aspect.AuditLogAspect;
import xyz.jasenon.lab.audit.persistence.AuditLogStore;
import xyz.jasenon.lab.audit.persistence.mapper.AuditLogMapper;
import xyz.jasenon.lab.web.audit.RpcAuditLogStore;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "dubbo.registry.address=N/A",
        "dubbo.config-center.address=N/A",
        "dubbo.consumer.check=false",
        "dubbo.consumer.init=false",
        "dubbo.consumer.lazy=true"
})
class WebApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context.getBean(AuditLogStore.class)).isInstanceOf(RpcAuditLogStore.class);
        assertThat(context.getBeansOfType(AuditLogAspect.class)).hasSize(1);
        assertThat(context.getBeansOfType(AuditLogMapper.class)).isEmpty();
    }

}
