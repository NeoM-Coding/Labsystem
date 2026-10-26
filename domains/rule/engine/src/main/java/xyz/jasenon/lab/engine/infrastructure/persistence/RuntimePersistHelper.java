package xyz.jasenon.lab.engine.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.engine.Engine;
import xyz.jasenon.lab.engine.compiler.RuntimeRevisionCompiler;
import xyz.jasenon.lab.engine.definition.RuntimeRevision;
import xyz.jasenon.lab.engine.infrastructure.persistence.itfc.RuntimePersist;
import xyz.jasenon.lab.engine.infrastructure.persistence.mapper.RuleRuntimeMapper;
import xyz.jasenon.lab.engine.infrastructure.persistence.mapper.RuleRuntimeRevisionMapper;
import xyz.jasenon.lab.engine.infrastructure.persistence.model.CurrentRuntimeRevision;
import xyz.jasenon.lab.engine.infrastructure.persistence.model.RuleRuntimeEntity;
import xyz.jasenon.lab.engine.infrastructure.persistence.model.RuleRuntimeRevisionEntity;
import xyz.jasenon.lab.engine.plan.RuntimePlan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Runtime definition 的 MySQL 持久化与内存 Engine 同步入口。
 *
 * <p>数据库保存不可变 revision；更新、启用和停用都会追加新版本。
 * 数据库事务提交成功后才替换 Engine 中的 Runtime。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "lab.rule-engine.persistence",
        name = "enabled",
        havingValue = "true"
)
public class RuntimePersistHelper implements RuntimePersist {

    private static final Logger log = LoggerFactory.getLogger(RuntimePersistHelper.class);
    private static final int SCHEMA_VERSION = 1;
    private static final String SYSTEM_USER = "system";

    private final RuleRuntimeMapper runtimeMapper;
    private final RuleRuntimeRevisionMapper revisionMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final RuntimeRevisionCompiler compiler;
    private final Engine engine;

    public RuntimePersistHelper(
            RuleRuntimeMapper runtimeMapper,
            RuleRuntimeRevisionMapper revisionMapper,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            RuntimeRevisionCompiler compiler,
            Engine engine
    ) {
        this.runtimeMapper = Objects.requireNonNull(runtimeMapper, "runtimeMapper");
        this.revisionMapper = Objects.requireNonNull(revisionMapper, "revisionMapper");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager")
        );
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    private static String currentActor() {
        UserContext context = UserContextHolder.get();
        return context == null || context.getUserId() == null || context.getUserId().isBlank()
                ? SYSTEM_USER
                : context.getUserId();
    }

    private static RuntimeRevision validateRevision(
            String expectedRuntimeId,
            RuntimeRevision revision
    ) {
        Objects.requireNonNull(revision, "revision");
        requireText(revision.runtimeId(), "revision.runtimeId");
        if (expectedRuntimeId != null && !expectedRuntimeId.equals(revision.runtimeId())) {
            throw new IllegalArgumentException(
                    "runtimeId does not match revision.runtimeId"
            );
        }
        return revision;
    }

    private static boolean shouldRun(RuntimeRevision revision) {
        return revision.isEnabled()
                && (revision.activeUntil() == null
                || Instant.now().isBefore(revision.activeUntil()));
    }

    private static String status(RuntimeRevision revision) {
        return revision.isEnabled() ? "PUBLISHED" : "DISABLED";
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    @Override
    public boolean register(RuntimeRevision revision) {
        RuntimeRevision checked = validateRevision(null, revision);
        RuntimePlan compiled = compiler.compile(checked);
        EncodedRevision encoded = encode(checked);

        try {
            Boolean inserted = transactionTemplate.execute(status -> {
                int metadataRows = runtimeMapper.insert(new RuleRuntimeEntity(
                        checked.runtimeId(),
                        checked.runtimeId(),
                        currentActor(),
                        checked.isEnabled(),
                        status(checked),
                        1,
                        checked.activeFrom(),
                        checked.activeUntil()
                ));
                if (metadataRows != 1) {
                    throw new IllegalStateException(
                            "failed to insert runtime metadata: " + checked.runtimeId()
                    );
                }
                if (insertRevision(checked.runtimeId(), 1, encoded) != 1) {
                    throw new IllegalStateException(
                            "failed to insert initial runtime revision: " + checked.runtimeId()
                    );
                }
                return true;
            });
            if (!Boolean.TRUE.equals(inserted)) {
                return false;
            }
        } catch (DuplicateKeyException e) {
            return false;
        }

        synchronizeEngine(checked, compiled);
        return true;
    }

    @Override
    public boolean update(String runtimeId, RuntimeRevision revision) {
        RuntimeRevision checked = validateRevision(runtimeId, revision);
        RuntimePlan compiled = compiler.compile(checked);
        EncodedRevision encoded = encode(checked);

        Boolean updated = transactionTemplate.execute(status -> {
            Integer currentRevision = lockCurrentRevision(runtimeId);
            if (currentRevision == null) {
                return false;
            }
            int nextRevision = currentRevision + 1;
            if (insertRevision(runtimeId, nextRevision, encoded) != 1) {
                throw new IllegalStateException(
                        "failed to insert runtime revision: " + runtimeId
                );
            }
            int metadataRows = runtimeMapper.publishRevision(
                    runtimeId,
                    checked.isEnabled(),
                    status(checked),
                    nextRevision,
                    checked.activeFrom(),
                    checked.activeUntil()
            );
            if (metadataRows != 1) {
                throw new IllegalStateException(
                        "failed to publish runtime revision: " + runtimeId
                );
            }
            return true;
        });
        if (!Boolean.TRUE.equals(updated)) {
            return false;
        }

        synchronizeEngine(checked, compiled);
        return true;
    }

    @Override
    public boolean remove(String runtimeId) {
        requireText(runtimeId, "runtimeId");
        Boolean removed = transactionTemplate.execute(status ->
                runtimeMapper.softDelete(runtimeId) == 1
        );
        if (Boolean.TRUE.equals(removed)) {
            engine.remove(runtimeId);
            return true;
        }
        return false;
    }

    @Override
    public boolean enable(String runtimeId) {
        return changeEnabled(runtimeId, true);
    }

    @Override
    public boolean disable(String runtimeId) {
        return changeEnabled(runtimeId, false);
    }

    @Override
    public RuntimeRevision get(String runtimeId) {
        requireText(runtimeId, "runtimeId");
        CurrentRuntimeRevision row = revisionMapper.selectCurrent(runtimeId);
        return row == null ? null : decode(row);
    }

    @Override
    public List<RuntimeRevision> fetch() {
        List<RuntimeRevision> revisions = new ArrayList<>();
        for (CurrentRuntimeRevision row : revisionMapper.selectAllCurrent()) {
            try {
                revisions.add(decode(row));
            } catch (IllegalStateException e) {
                log.error(
                        "[RuleEngine] skip malformed runtime revision, runtime-id:{}",
                        row.getRuntimeId(),
                        e
                );
            }
        }
        return List.copyOf(revisions);
    }

    /**
     * 服务启动完成后恢复所有当前启用且未过期的 Runtime。
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void restoreEnabledRuntimes() {
        List<RuntimePlan> restored = new ArrayList<>();
        int skipped = 0;
        for (RuntimeRevision revision : fetch()) {
            if (!shouldRun(revision)) {
                skipped++;
                continue;
            }
            try {
                RuntimePlan plan = compiler.compile(revision);
                engine.register(plan);
                restored.add(plan);
            } catch (RuntimeException e) {
                skipped++;
                log.error(
                        "[RuleEngine] failed to restore runtime revision, runtime-id:{}",
                        revision.runtimeId(),
                        e
                );
            }
        }
        log.info(
                "[RuleEngine] runtime persistence restore completed, restored:{}, skipped:{}",
                restored.size(),
                skipped
        );
    }

    private boolean changeEnabled(String runtimeId, boolean enabled) {
        requireText(runtimeId, "runtimeId");
        EnabledChange change = transactionTemplate.execute(status -> {
            Integer currentRevision = lockCurrentRevision(runtimeId);
            if (currentRevision == null) {
                return null;
            }
            RuntimeRevision current = readCurrentRevision(runtimeId);
            RuntimeRevision desired = current.withEnabled(enabled);
            RuntimePlan compiled = enabled ? compiler.compile(desired) : null;
            if (current.isEnabled() == enabled) {
                return new EnabledChange(desired, compiled);
            }

            int nextRevision = currentRevision + 1;
            EncodedRevision encoded = encode(desired);
            if (insertRevision(runtimeId, nextRevision, encoded) != 1) {
                throw new IllegalStateException(
                        "failed to insert enabled runtime revision: " + runtimeId
                );
            }
            int metadataRows = runtimeMapper.publishEnabledRevision(
                    runtimeId,
                    desired.isEnabled(),
                    status(desired),
                    nextRevision
            );
            if (metadataRows != 1) {
                throw new IllegalStateException(
                        "failed to publish enabled runtime revision: " + runtimeId
                );
            }
            return new EnabledChange(desired, compiled);
        });
        if (change == null) {
            return false;
        }
        synchronizeEngine(change.revision(), change.compiled());
        return true;
    }

    private RuntimeRevision readCurrentRevision(String runtimeId) {
        CurrentRuntimeRevision row = revisionMapper.selectCurrent(runtimeId);
        if (row == null) {
            throw new IllegalStateException(
                    "runtime disappeared while metadata row was locked: " + runtimeId
            );
        }
        return decode(row);
    }

    private Integer lockCurrentRevision(String runtimeId) {
        return runtimeMapper.lockCurrentRevision(runtimeId);
    }

    private int insertRevision(
            String runtimeId,
            int revisionNo,
            EncodedRevision encoded
    ) {
        return revisionMapper.insert(new RuleRuntimeRevisionEntity(
                runtimeId,
                revisionNo,
                SCHEMA_VERSION,
                encoded.json(),
                encoded.checksum(),
                currentActor()
        ));
    }

    private RuntimeRevision decode(CurrentRuntimeRevision row) {
        try {
            RuntimeRevision revision = objectMapper.readValue(
                    row.getDefinition(),
                    RuntimeRevision.class
            );
            return revision.withEnabled(Boolean.TRUE.equals(row.getEnabled()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "invalid runtime revision JSON: " + row.getRuntimeId(),
                    e
            );
        }
    }

    private void synchronizeEngine(RuntimeRevision revision, RuntimePlan compiled) {
        if (!shouldRun(revision)) {
            engine.remove(revision.runtimeId());
            return;
        }
        engine.register(compiled);
    }

    private EncodedRevision encode(RuntimeRevision revision) {
        try {
            String json = objectMapper.writeValueAsString(revision);
            return new EncodedRevision(json, sha256(json));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("runtime revision cannot be serialized", e);
        }
    }

    private record EncodedRevision(String json, String checksum) {
    }

    private record EnabledChange(RuntimeRevision revision, RuntimePlan compiled) {
    }

}
