package xyz.jasenon.lab.auth.service;

import java.util.Set;

public record LaboratoryMembers(Set<String> ownerIds, Set<String> viewerIds) {
    public LaboratoryMembers {
        ownerIds = ownerIds == null ? Set.of() : Set.copyOf(ownerIds);
        viewerIds = viewerIds == null ? Set.of() : Set.copyOf(viewerIds);
    }
}
