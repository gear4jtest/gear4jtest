package io.github.gear4jtest.core.model.refactor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import io.github.gear4jtest.core.execution.FlushPolicy;
import io.github.gear4jtest.core.execution.IteratorBatch;
import io.github.gear4jtest.core.execution.PipelineExecutionManager;
import io.github.gear4jtest.core.execution.ReportGranularity;
import io.github.gear4jtest.core.persistence.OperationExecutionRecord;

/**
 * Itérateur d'opérations :
 * - conserve le builder et les accumulateurs d'origine
 * - n'utilise plus ExecutionReport / OperationResult
 * - branche sur PipelineExecutionManager + IteratorBatch / OperationExecutionRecord
 */
@SuppressWarnings("unchecked")
public class IteratorDefinition<IN, OUT> extends AbstractOperationDefinition<IN, OUT> {

	private Function<IN, ? extends Iterable<?>> func;
	private AssemblyLineDefinition assemblyLineDefinition;
	private AbstractOperationDefinition operation;
	private Accumulator accumulator;
	private Collector collector;

	/** Politique de flush (par défaut : flush tous les 1000 éléments). */
	private FlushPolicy flushPolicy = FlushPolicy.byCount(1000);

	/** Granularité des reports (par défaut ITEM : un record par élément). */
	private ReportGranularity reportGranularity = ReportGranularity.ITEM;

	public IteratorDefinition(String id) {
		super(id, OperationKind.ITERATOR);
	}

	public Function<IN, ? extends Iterable<?>> getFunc() {
		return func;
	}

	public Accumulator getAccumulator() {
		return accumulator;
	}

	public Collector getCollector() {
		return collector;
	}

	/** Configuration programmatique du flush (sans toucher au builder). */
	public IteratorDefinition<IN, OUT> flushPolicy(FlushPolicy flushPolicy) {
		this.flushPolicy = flushPolicy;
		return this;
	}

	/** Configuration programmatique de la granularité de report. */
	public IteratorDefinition<IN, OUT> reportGranularity(ReportGranularity reportGranularity) {
		this.reportGranularity = reportGranularity;
		return this;
	}

	@Override
	public OUT doExecute(IN input, ExecutionContext context, OperationExecutionContext operationExecution) {
		Iterable<?> collection;
		if (func != null) {
			collection = func.apply(input);
		} else {
			collection = (Iterable<?>) input;
		}

		Collection<Object> results = new ArrayList<>();

		// Stats / batch pour flush
		long index = 0L;
		long batchStartIndex = 0L;
		long batchCount = 0L;
		Instant batchStartTime = Instant.now();

		Map<String, Long> operationCounts = new HashMap<>();
		Collection<OperationExecutionRecord> batchRecords = new ArrayList<>();

		PipelineExecutionManager manager = context.getExecutionManager();
		UUID pipelineExecutionId = context.getExecutionId();

		for (Object element : collection) {
			// L'opération interne (AbstractOperationDefinition) est exécutée via son run(...)
			OperationExecutionRecord rec =
					((OperationDefinition<Object, Object>) operation).run(element, context);

			// En option : rattacher le parent (l'iterator) si tu le souhaites
			// rec.setParentOperationId(operationExecution.getReport().getId().toString());

			// Output fonctionnel pour la suite de la pipeline
			Object value = rec.getOutput(Object.class);
			results.add(value);

			// Stats batch
			index++;
			batchCount++;
			operationCounts.merge(rec.getOperationId(), 1L, Long::sum);

			if (reportGranularity == ReportGranularity.ITEM) {
				batchRecords.add(rec);
			}

			boolean failed = rec.getStatus() == OperationExecutionRecord.Status.FAILED;

			if (failed || shouldFlush(batchCount, batchStartTime)) {
				flushBatch(
						context,
						manager,
						pipelineExecutionId,
						batchStartIndex,
						index - 1,
						batchStartTime,
						Instant.now(),
						operationCounts,
						batchRecords
				);

				// reset stats / buffer
				batchStartIndex = index;
				batchStartTime = Instant.now();
				batchCount = 0L;
				operationCounts = new HashMap<>();
				batchRecords = new ArrayList<>();
			}

			// on arrête l'itération en cas de failure (comportement d'origine)
			if (failed) {
				break;
			}
		}

		// flush final si nécessaire
		if (batchCount > 0) {
			flushBatch(
					context,
					manager,
					pipelineExecutionId,
					batchStartIndex,
					index - 1,
					batchStartTime,
					Instant.now(),
					operationCounts,
					batchRecords
			);
		}

		// Accumulateur custom (toujours compatible avec ta classe Accumulator)
		if (accumulator != null) {
			Collection<Object> acc = accumulator.getCollectionSupplier().getSupplier().get();
			acc.addAll(results);
			return (OUT) acc;
		}

		// Collector Java Stream si défini
		if (collector != null) {
			return (OUT) results.stream().collect(collector);
		}

		// Sinon on renvoie simplement la collection des résultats
		return (OUT) results;
	}

	private boolean shouldFlush(long batchCount, Instant batchStartTime) {
		if (flushPolicy == null) {
			return false;
		}

		switch (flushPolicy.type()) {
			case BY_COUNT:
				return batchCount >= flushPolicy.count();
			case BY_TIME:
				return flushPolicy.every() != null &&
						Instant.now().isAfter(batchStartTime.plus(flushPolicy.every()));
			case BY_MEMORY:
			default:
				// Non géré finement ici : on laisse la politique mémoire pour plus tard
				return false;
		}
	}

	private void flushBatch(ExecutionContext context,
							PipelineExecutionManager manager,
							UUID pipelineExecutionId,
							long startIndexInclusive,
							long endIndexInclusive,
							Instant startedAt,
							Instant endedAt,
							Map<String, Long> operationCounts,
							Collection<OperationExecutionRecord> batchRecords) {

		if (reportGranularity == ReportGranularity.ITEM) {
			// Mode ITEM : on push chaque record individuellement
			for (OperationExecutionRecord rec : batchRecords) {
				manager.append(rec);
			}
		} else {
			// Mode BATCH / SUMMARY : on crée un IteratorBatch agrégé
			// (ici on envoie tous les records en samples, à affiner au besoin)
			IteratorBatch batch = new IteratorBatch(
					pipelineExecutionId,
					getId(),
					startIndexInclusive,
					endIndexInclusive,
					startedAt,
					endedAt,
					new HashMap<>(operationCounts),
					new ArrayList<>(batchRecords),
					null // summaryJson (optionnel, à remplir si tu veux)
			);
			manager.append(batch);
		}
	}

	// --------------------------------------------------------------------------------------------
	// Builder ORIGINAL (conservé tel quel)
	// --------------------------------------------------------------------------------------------
	public static class Builder<IN, OUT> {

		private final IteratorDefinition<IN, OUT> managedInstance;

		public Builder(String id) {
			managedInstance = new IteratorDefinition<>(id);
		}

		public <A> Builder<IN, A> iterableFunction(Function<IN, ? extends Iterable<A>> func) {
			managedInstance.func = func;
			return (Builder<IN, A>) this;
		}

		public <A> Builder<IN, A> pipeline(AssemblyLineDefinition<OUT, A> assemblyLineDefinition) {
			managedInstance.assemblyLineDefinition = assemblyLineDefinition;
			return (Builder<IN, A>) this;
		}

		public <A> Builder<IN, A> operation(AbstractOperationDefinition<OUT, A> operation) {
			managedInstance.operation = operation;
			return (Builder<IN, A>) this;
		}

		public Builder<IN, OUT> accumulator(Accumulator accumulator) {
			managedInstance.accumulator = accumulator;
			return this;
		}

		public <C> Builder<IN, C> collector(Collector<OUT, ?, C> collector) {
			managedInstance.collector = collector;
			return (Builder<IN, C>) this;
		}

		public IteratorDefinition<IN, OUT> build() {
			return managedInstance;
		}
	}

	// --------------------------------------------------------------------------------------------
	// Accumulateurs ORIGINAUX (inchangés)
	// --------------------------------------------------------------------------------------------
	public static class Accumulator {

		private final CollectionSupplier collectionSupplier;

		public Accumulator(CollectionSupplier collectionSupplier) {
			this.collectionSupplier = collectionSupplier;
		}

		public CollectionSupplier getCollectionSupplier() {
			return collectionSupplier;
		}

		public enum CollectionSupplier {
			LIST(ArrayList::new),
			SET(HashSet::new);

			private final Supplier<Collection<Object>> supplier;

			CollectionSupplier(Supplier<Collection<Object>> supplier) {
				this.supplier = supplier;
			}

			public Supplier<Collection<Object>> getSupplier() {
				return supplier;
			}
		}
	}

	public static class ListAccumulator extends Accumulator {
		public ListAccumulator() {
			super(CollectionSupplier.LIST);
		}
	}

	public static class SetAccumulator extends Accumulator {
		public SetAccumulator() {
			super(CollectionSupplier.SET);
		}
	}
}
