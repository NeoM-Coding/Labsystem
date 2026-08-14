package xyz.jasenon.lab.engine.eval.v2.demo;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** React 演示页使用的测试专用 HTTP 入口。 */
@RestController
@RequestMapping("/api/eval-v2-demo")
@CrossOrigin(origins = {"http://localhost:5174", "http://127.0.0.1:5174"})
public class EvalV2DemoController {

    private final EvalV2DemoForest demoForest;

    public EvalV2DemoController(EvalV2DemoForest demoForest) {
        this.demoForest = demoForest;
    }

    @GetMapping("/forest")
    public EvalV2DemoForest.ForestSnapshot forest() {
        return demoForest.snapshot();
    }

    @PostMapping("/events")
    public EvalV2DemoForest.EventResult accept(@RequestBody EvalV2DemoForest.EventRequest request) {
        return demoForest.accept(request);
    }

    @PostMapping("/reset")
    public EvalV2DemoForest.ForestSnapshot reset() {
        return demoForest.reset();
    }
}
