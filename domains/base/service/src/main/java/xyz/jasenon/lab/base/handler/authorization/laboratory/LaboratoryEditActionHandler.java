package xyz.jasenon.lab.base.handler.authorization.laboratory;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.base.api.dto.LaboratoryEdit;
import xyz.jasenon.lab.base.handler.authorization.AbstractAppActionCommandHandler;

@Component
public class LaboratoryEditActionHandler
        extends AbstractAppActionCommandHandler<LaboratoryEdit> {

    public LaboratoryEditActionHandler() {
        super(LaboratoryEdit.class, Action.App.manage_laboratory);
    }
}
