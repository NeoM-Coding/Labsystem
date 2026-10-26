package xyz.jasenon.lab.engine.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import xyz.jasenon.lab.engine.Engine;
import xyz.jasenon.lab.engine.compiler.RuntimeRevisionCompiler;
import xyz.jasenon.lab.engine.definition.RuntimeRevision;
import xyz.jasenon.lab.engine.execution.ActionDispatcher;
import xyz.jasenon.lab.engine.infrastructure.persistence.mapper.RuleRuntimeMapper;
import xyz.jasenon.lab.engine.infrastructure.persistence.mapper.RuleRuntimeRevisionMapper;
import xyz.jasenon.lab.engine.infrastructure.persistence.model.CurrentRuntimeRevision;
import xyz.jasenon.lab.engine.infrastructure.persistence.model.RuleRuntimeEntity;
import xyz.jasenon.lab.engine.infrastructure.persistence.model.RuleRuntimeRevisionEntity;
import xyz.jasenon.lab.engine.scheduling.EngineOptions;
import xyz.jasenon.lab.engine.scheduling.TimeScheduler;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tests the persistence lifecycle with isolated mapper and transaction boundaries. */
class RuntimePersistHelperTests {
    private final RuleRuntimeMapper runtimeMapper = mock(RuleRuntimeMapper.class);
    private final RuleRuntimeRevisionMapper revisionMapper = mock(RuleRuntimeRevisionMapper.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private Engine engine;
    private TimeScheduler timers;
    private ActionDispatcher actions;
    private RuntimePersistHelper helper;

    @BeforeEach
    void setUp() {
        EngineOptions options = new EngineOptions();
        options.setWorkers(1);
        options.setExecutionThreads(1);
        actions = new ActionDispatcher((runtime, group, action) -> {
            throw new AssertionError("Persistence tests must not execute device actions");
        }, notice -> {}, options);
        timers = new TimeScheduler();
        engine = new Engine(actions, timers, options);
        when(transactions.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        helper = new RuntimePersistHelper(runtimeMapper, revisionMapper, json, transactions,
                new RuntimeRevisionCompiler(), engine);
    }

    @AfterEach
    void tearDown() {
        engine.close();
        timers.close();
        actions.close();
    }

    @Test
    void registersInitialRevisionAndSynchronizesEngineAfterCommit() throws Exception {
        when(runtimeMapper.insert(any(RuleRuntimeEntity.class))).thenReturn(1);
        when(revisionMapper.insert(any(RuleRuntimeRevisionEntity.class))).thenReturn(1);
        doAnswer(invocation -> {
            assertTrue(engine.runtime("runtime-1").isEmpty());
            return null;
        }).when(transactions).commit(any());

        assertTrue(helper.register(revision(true)));

        var saved = ArgumentCaptor.forClass(RuleRuntimeRevisionEntity.class);
        verify(revisionMapper).insert(saved.capture());
        assertEquals(1, saved.getValue().getRevisionNo());
        assertEquals("runtime-1", saved.getValue().getRuntimeId());
        assertEquals(revision(true), json.readValue(saved.getValue().getDefinition(), RuntimeRevision.class));
        assertTrue(engine.runtime("runtime-1").isPresent());
    }

    @Test
    void duplicateRegistrationDoesNotPublishToEngine() {
        when(runtimeMapper.insert(any(RuleRuntimeEntity.class)))
                .thenThrow(new DuplicateKeyException("duplicate runtime"));

        assertFalse(helper.register(revision(true)));

        verifyNoInteractions(revisionMapper);
        verify(transactions).rollback(any());
        assertTrue(engine.runtime("runtime-1").isEmpty());
    }

    @Test
    void failedRevisionWriteRollsBackAndDoesNotPublishToEngine() {
        when(runtimeMapper.insert(any(RuleRuntimeEntity.class))).thenReturn(1);
        when(revisionMapper.insert(any(RuleRuntimeRevisionEntity.class))).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> helper.register(revision(true)));

        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
        assertTrue(engine.runtime("runtime-1").isEmpty());
    }

    @Test
    void failedCommitDoesNotPublishToEngine() {
        when(runtimeMapper.insert(any(RuleRuntimeEntity.class))).thenReturn(1);
        when(revisionMapper.insert(any(RuleRuntimeRevisionEntity.class))).thenReturn(1);
        doThrow(new IllegalStateException("commit failed")).when(transactions).commit(any());

        assertThrows(IllegalStateException.class, () -> helper.register(revision(true)));
        assertTrue(engine.runtime("runtime-1").isEmpty());
    }

    @Test
    void updateAppendsRevisionAndPublishesItsNumber() {
        when(runtimeMapper.lockCurrentRevision("runtime-1")).thenReturn(3);
        when(revisionMapper.insert(any(RuleRuntimeRevisionEntity.class))).thenReturn(1);
        when(runtimeMapper.publishRevision("runtime-1", true, "PUBLISHED", 4, null, null)).thenReturn(1);

        assertTrue(helper.update("runtime-1", revision(true)));

        var saved = ArgumentCaptor.forClass(RuleRuntimeRevisionEntity.class);
        verify(revisionMapper).insert(saved.capture());
        assertEquals(4, saved.getValue().getRevisionNo());
        assertTrue(engine.runtime("runtime-1").isPresent());
    }

    @Test
    void missingRuntimeCannotBeUpdatedOrEnabled() {
        when(runtimeMapper.lockCurrentRevision("runtime-1")).thenReturn(null);
        assertFalse(helper.update("runtime-1", revision(true)));
        assertFalse(helper.enable("runtime-1"));
        verifyNoInteractions(revisionMapper);
        assertEquals(0, engine.runtimeCount());
    }

    @Test
    void unchangedEnabledStateDoesNotAppendRevision() throws Exception {
        when(runtimeMapper.lockCurrentRevision("runtime-1")).thenReturn(2);
        when(revisionMapper.selectCurrent("runtime-1")).thenReturn(row(revision(true)));

        assertTrue(helper.enable("runtime-1"));

        verify(revisionMapper, never()).insert(any(RuleRuntimeRevisionEntity.class));
        verify(runtimeMapper, never()).publishEnabledRevision(anyString(), anyBoolean(), anyString(), anyInt());
        assertTrue(engine.runtime("runtime-1").isPresent());
    }

    @Test
    void disableAppendsRevisionAndRemovesRuntimeFromEngine() throws Exception {
        engine.register(new RuntimeRevisionCompiler().compile(revision(true)));
        when(runtimeMapper.lockCurrentRevision("runtime-1")).thenReturn(2);
        when(revisionMapper.selectCurrent("runtime-1")).thenReturn(row(revision(true)));
        when(revisionMapper.insert(any(RuleRuntimeRevisionEntity.class))).thenReturn(1);
        when(runtimeMapper.publishEnabledRevision("runtime-1", false, "DISABLED", 3)).thenReturn(1);

        assertTrue(helper.disable("runtime-1"));

        var saved = ArgumentCaptor.forClass(RuleRuntimeRevisionEntity.class);
        verify(revisionMapper).insert(saved.capture());
        assertEquals(3, saved.getValue().getRevisionNo());
        assertFalse(json.readValue(saved.getValue().getDefinition(), RuntimeRevision.class).isEnabled());
        assertTrue(engine.runtime("runtime-1").isEmpty());
    }

    @Test
    void removesRuntimeOnlyWhenMetadataWasDeleted() {
        engine.register(new RuntimeRevisionCompiler().compile(revision(true)));
        when(runtimeMapper.softDelete("runtime-1")).thenReturn(0, 1);

        assertFalse(helper.remove("runtime-1"));
        assertTrue(engine.runtime("runtime-1").isPresent());
        assertTrue(helper.remove("runtime-1"));
        assertTrue(engine.runtime("runtime-1").isEmpty());
    }

    @Test
    void restoreSkipsInvalidRevisionsAndRestoresTimePoints() throws Exception {
        RuntimeRevision expired = new RuntimeRevision("expired", true, null, Instant.EPOCH,
                List.of(), List.of(), List.of());
        var point = new RuntimeRevision.TimeConditionDefinition("point", RuntimeRevision.TimeConditionType.TIME_POINT,
                null, null, Set.of(), "UTC", null, null, java.time.LocalTime.NOON);
        RuntimeRevision unsupported = new RuntimeRevision("legacy", true, null, null, List.of(),
                List.of(new RuntimeRevision.TimeConditionGroupDefinition("always", List.of(point))), List.of());
        CurrentRuntimeRevision malformed = row(revision(true));
        malformed.setDefinition("invalid-json");
        when(revisionMapper.selectAllCurrent()).thenReturn(List.of(
                row(revision(false)), row(expired), malformed, row(unsupported),
                row(new RuntimeRevision("enabled", true, null, null, List.of(), List.of(), List.of()))));

        helper.restoreEnabledRuntimes();

        assertEquals(2, engine.runtimeCount());
        assertTrue(engine.runtime("enabled").isPresent());
        assertTrue(engine.runtime("legacy").isPresent());
    }

    @Test
    void rejectsMismatchedRuntimeIdBeforeWriting() {
        assertThrows(IllegalArgumentException.class, () -> helper.update("other", revision(true)));
        verifyNoInteractions(runtimeMapper, revisionMapper, transactions);
    }

    @Test
    void publishesTimePointWithoutChangingStoredFormat() {
        var point = new RuntimeRevision.TimeConditionDefinition("point", RuntimeRevision.TimeConditionType.TIME_POINT,
                null, null, Set.of(), "UTC", null, null, java.time.LocalTime.NOON);
        var unsupported = new RuntimeRevision("runtime-1", true, null, null, List.of(),
                List.of(new RuntimeRevision.TimeConditionGroupDefinition("always", List.of(point))), List.of());

        when(runtimeMapper.insert(any(RuleRuntimeEntity.class))).thenReturn(1);
        when(revisionMapper.insert(any(RuleRuntimeRevisionEntity.class))).thenReturn(1);
        assertTrue(helper.register(unsupported));
        assertTrue(engine.runtime("runtime-1").isPresent());
        verify(transactions).commit(any());
    }

    private RuntimeRevision revision(boolean enabled) {
        return new RuntimeRevision("runtime-1", enabled, null, null, List.of(), List.of(), List.of());
    }

    private CurrentRuntimeRevision row(RuntimeRevision revision) throws Exception {
        var row = new CurrentRuntimeRevision();
        row.setRuntimeId(revision.runtimeId());
        row.setEnabled(revision.isEnabled());
        row.setDefinition(json.writeValueAsString(revision));
        return row;
    }
}
