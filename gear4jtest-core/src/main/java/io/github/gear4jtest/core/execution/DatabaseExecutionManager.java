package io.github.gear4jtest.core.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.DatabasePipelineExecutionRepository;
import io.github.gear4jtest.core.persistence.OperationExecutionRecord;
import io.github.gear4jtest.core.persistence.PipelineExecution;

/**
 * ExecutionManager pour persister en DB :
 *  - bufferise les OperationExecutionRecord par pipeline,
 *  - flush en batch tous les N records (par pipeline),
 *  - flush final à la fin de l'exécution.
 */
public class DatabaseExecutionManager implements PipelineExecutionManager {

    private final DatabasePipelineExecutionRepository repository;

    /**
     * Buffer des opérations par pipeline.
     */
    private final Map<UUID, ConcurrentLinkedQueue<OperationExecutionRecord>> opBuffers =
            new ConcurrentHashMap<>();

    /**
     * Seuil de flush automatique (nombre de records en mémoire par pipeline).
     */
    private final int flushThreshold;

    public DatabaseExecutionManager(DataSource dataSource) {
        this(dataSource, 500, true); // par défaut : flush tous les 500 records
    }

    public DatabaseExecutionManager(DataSource dataSource, int flushThreshold, boolean autoCreateTables) {
        this.repository = new DatabasePipelineExecutionRepository(dataSource);
        this.flushThreshold = flushThreshold;
        if (autoCreateTables) {
            this.repository.initialize();
        }
    }

    @Override
    public void start(PipelineExecution execution) {
        // On persiste l'exécution elle-même (sans ses opérations pour l'instant)
        repository.save(execution);
        // On initialise le buffer d'opérations pour ce pipeline
        opBuffers.put(execution.getId(), new ConcurrentLinkedQueue<>());
    }

    @Override
    public void append(OperationExecutionRecord record) {
        if (record == null) {
            return;
        }

        UUID pipelineId = UUID.fromString(record.getPipelineExecutionId());
        ConcurrentLinkedQueue<OperationExecutionRecord> queue =
                opBuffers.computeIfAbsent(pipelineId, id -> new ConcurrentLinkedQueue<>());

        queue.add(record);

        // Si on dépasse le seuil, flush immédiat pour ce pipeline
        if (queue.size() >= flushThreshold) {
            flush(pipelineId);
        }
    }

    @Override
    public void appendAll(List<OperationExecutionRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        // On suppose que tous les records appartiennent au même pipeline,
        // ce qui est le cas si l'orchestrateur fait bien son boulot.
        String pipelineExecutionId = records.get(0).getPipelineExecutionId();
        UUID pipelineId = UUID.fromString(pipelineExecutionId);

        ConcurrentLinkedQueue<OperationExecutionRecord> queue =
                opBuffers.computeIfAbsent(pipelineId, id -> new ConcurrentLinkedQueue<>());

        queue.addAll(records);

        if (queue.size() >= flushThreshold) {
            flush(pipelineId);
        }
    }

    @Override
    public void flush(UUID pipelineId) {
        ConcurrentLinkedQueue<OperationExecutionRecord> queue = opBuffers.get(pipelineId);
        if (queue == null || queue.isEmpty()) {
            return;
        }

        List<OperationExecutionRecord> batch = new ArrayList<>(flushThreshold);

        OperationExecutionRecord rec;
        while ((rec = queue.poll()) != null) {
            batch.add(rec);
            if (batch.size() >= flushThreshold) {
                repository.saveOperationsBatch(batch);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            repository.saveOperationsBatch(batch);
        }
    }

    @Override
    public void end(PipelineExecution finalExecution) {
        UUID pipelineId = finalExecution.getId();

        // Flush final des records pour ce pipeline
        flush(pipelineId);

        // Mise à jour de l'exécution (status, end_time, result, context, etc.)
        repository.update(finalExecution);

        // Nettoyage du buffer
        opBuffers.remove(pipelineId);
    }

    @Override
    public void shutdown() {
        // Flush de tout ce qui reste avant shutdown
        for (UUID pipelineId : opBuffers.keySet()) {
            flush(pipelineId);
        }
        opBuffers.clear();
    }
}
