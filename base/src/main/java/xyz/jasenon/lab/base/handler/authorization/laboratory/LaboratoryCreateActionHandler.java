package xyz.jasenon.lab.base.handler.authorization.laboratory;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.base.api.dto.LaboratoryCreate;
import xyz.jasenon.lab.base.handler.authorization.AbstractAppActionCommandHandler;

@Component
public class LaboratoryCreateActionHandler
        extends AbstractAppActionCommandHandler<LaboratoryCreate> {

    public LaboratoryCreateActionHandler() {
        super(LaboratoryCreate.class, Action.App.manage_laboratory);
    }
}
