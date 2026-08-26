package xyz.jasenon.lab.base.handler.authorization.laboratory;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.command.ActionCommand;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.handler.ActionCommandHandler;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.base.api.dto.LaboratoryMemberQuery;

@Component
public class LaboratoryMemberQueryActionHandler extends ActionCommandHandler<LaboratoryMemberQuery> {
    public LaboratoryMemberQueryActionHandler() {
        super(LaboratoryMemberQuery.class);
    }

    @Override
    protected ActionCommand toAction(LaboratoryMemberQuery source, UserContext context) {
        return new ActionCommand(SourceType.laboratory, source.laboratoryId(),
                Action.Laboratory.laboratory_manage, SourceType.user, context.getUserId());
    }
}
