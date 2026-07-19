package xyz.jasenon.lab.web.laboratory;

import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.jasenon.lab.base.api.dto.LaboratoryDelete;
import xyz.jasenon.lab.base.api.dto.LaboratoryCreate;
import xyz.jasenon.lab.base.api.dto.LaboratoryEdit;
import xyz.jasenon.lab.base.api.model.Laboratory;
import xyz.jasenon.lab.base.api.service.LaboratoryService;
import xyz.jasenon.lab.base.api.vo.LaboratoryVO;
import xyz.jasenon.lab.common.util.Pair;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/laboratories")
@Traced
public class LaboratoryController {

    @DubboReference(check = false)
    private LaboratoryService laboratoryService;

    @GetMapping
    public DiyResponseEntity<R<List<LaboratoryVO>>> list(
            @RequestParam(required = false) String buildingName,
            @RequestParam(required = false) String orgName) {
        return DiyResponseEntity.of(R.success(laboratoryService.list(buildingName, orgName)));
    }

    @GetMapping("/options/organizations")
    public DiyResponseEntity<R<List<Pair<String, String>>>> organizations() {
        return DiyResponseEntity.of(R.success(laboratoryService.collectionOrgName()));
    }

    @GetMapping("/options/buildings")
    public DiyResponseEntity<R<List<Pair<String, String>>>> buildings() {
        return DiyResponseEntity.of(R.success(laboratoryService.collectionBuildingName()));
    }

    @PostMapping
    public DiyResponseEntity<R<Laboratory>> create(@RequestBody LaboratoryCreate command) {
        return DiyResponseEntity.of(R.success(laboratoryService.create(command)));
    }

    @PutMapping("/{laboratoryId}")
    public DiyResponseEntity<R<Laboratory>> update(@PathVariable String laboratoryId,
                                                   @RequestBody LaboratoryEdit command) {
        // 使用 path ID 重新组装下游 Command，同时保留已有授权 Handler 的 DTO 契约。
        LaboratoryEdit downstream = new LaboratoryEdit(
                laboratoryId,
                command.buildingName(),
                command.orgName(),
                command.laboratoryName(),
                command.extra(),
                command.manager()
        );
        return DiyResponseEntity.of(R.success(laboratoryService.update(downstream)));
    }

    @DeleteMapping("/{laboratoryId}")
    public DiyResponseEntity<R<Void>> delete(@PathVariable String laboratoryId) {
        laboratoryService.delete(new LaboratoryDelete(laboratoryId, null));
        return DiyResponseEntity.of(R.success());
    }
}
