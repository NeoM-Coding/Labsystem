package xyz.jasenon.lab.auth.client;

import co.permify.sdk.model.TupleFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AuthClientRelationshipReadTests {

    @Test
    void relationshipReadRequestIncludesRequiredMetadata() {
        TupleFilter filter = new TupleFilter();

        var body = AuthClient.relationshipReadBody(filter, "next-page");

        assertNotNull(body.getMetadata());
        assertEquals("", body.getMetadata().getSnapToken());
        assertSame(filter, body.getFilter());
        assertEquals(100L, body.getPageSize());
        assertEquals("next-page", body.getContinuousToken());
    }
}
