package xyz.jasenon.lab.base.handler.authorization.laboratory;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.base.api.dto.LaboratoryDelete;
import xyz.jasenon.lab.base.handler.authorization.AbstractAppActionCommandHandler;

@Component
public class LaboratoryDeleteActionHandler
        extends AbstractAppActionCommandHandler<LaboratoryDelete> {

    public LaboratoryDeleteActionHandler() {
        super(LaboratoryDelete.class, Action.App.manage_laboratory);
    }
}
