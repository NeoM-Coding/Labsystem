package xyz.jasenon.lab.mqtt.client;

import xyz.jasenon.lab.common.error.ErrorCode;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.common.exception.BusinessException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class VisibleLaboratoryScope {


    public List<String> resolve(List<String> requestedIds) {
        UserContext context = UserContextHolder.get();
        if (context == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "登陆已过期");
        }
        Set<String> visibleIds = new LinkedHashSet<>(context.filterLaboratoryIds());
        if (requestedIds == null || requestedIds.isEmpty()) {
            return List.copyOf(visibleIds);
        }
        return requestedIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .filter(visibleIds::contains)
                .distinct()
                .toList();
    }
}
