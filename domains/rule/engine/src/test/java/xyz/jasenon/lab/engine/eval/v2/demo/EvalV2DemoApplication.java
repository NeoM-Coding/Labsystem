package xyz.jasenon.lab.engine.eval.v2.demo;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

/** 仅存在于 test classpath 的 Eval v2 演示服务。 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@Import({EvalV2DemoForest.class, EvalV2DemoController.class})
public class EvalV2DemoApplication {
}
