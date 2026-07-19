package xyz.jasenon.lab.base.handler.authorization.user;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.base.api.dto.ContactUserCreate;
import xyz.jasenon.lab.base.handler.authorization.AbstractAppActionCommandHandler;

@Component
public class ContactUserCreateActionHandler
        extends AbstractAppActionCommandHandler<ContactUserCreate> {

    public ContactUserCreateActionHandler() {
        super(ContactUserCreate.class, Action.App.create_user);
    }
}
