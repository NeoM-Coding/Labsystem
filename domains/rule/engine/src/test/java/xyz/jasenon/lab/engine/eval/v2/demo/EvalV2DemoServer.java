package xyz.jasenon.lab.engine.eval.v2.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;

/**
 * 显式运行这个测试即可启动演示服务；它不会被默认测试扫描自动执行。
 */
@SpringBootTest(
        classes = EvalV2DemoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=${eval.v2.demo.port:18082}",
                "spring.application.name=eval-v2-demo",
                "spring.main.banner-mode=off",
                "dubbo.enabled=false",
                "lab.redis.enabled=false",
                "lab.rule-engine.persistence.enabled=false",
                "fun.uid.assigner-mode=none"
        }
)
@ActiveProfiles("test")
class EvalV2DemoServer {

    @Test
    void serveUntilInterrupted() throws InterruptedException {
        // 类名不匹配 Surefire 的默认测试扫描规则，只有被显式运行时才会进入这里。
        // 因此直接保持服务存活，IntelliJ 和命令行都无需额外设置 VM 参数。
        new CountDownLatch(1).await();
    }
}
