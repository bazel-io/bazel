// Copyright 2014 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.skyframe;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.build.lib.concurrent.ThreadSafety.ThreadHostile;
import com.google.devtools.build.lib.events.ExtendedEventHandler;
import java.io.PrintStream;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * A graph, defined by a set of functions that can construct values from value keys.
 *
 * <p>The value constructor functions ({@link SkyFunction}s) can declare dependencies on
 * prerequisite {@link SkyValue}s. The {@link MemoizingEvaluator} implementation makes sure that
 * those are created beforehand.
 *
 * <p>The graph caches previously computed values. Arbitrary values can be invalidated between calls
 * to {@link #evaluate}; they will be recreated the next time they are requested.
 */
public interface MemoizingEvaluator {

  /**
   * Computes the transitive closure of a given set of values. See {@link
   * EagerInvalidator#invalidate}.
   *
   * <p>The returned EvaluationResult is guaranteed to contain a result for at least one root if
   * keepGoing is false. It will contain a result for every root if keepGoing is true, <i>unless</i>
   * the evaluation failed with a "catastrophic" error. In that case, some or all results may be
   * missing.
   */
  <T extends SkyValue> EvaluationResult<T> evaluate(
      Iterable<? extends SkyKey> roots, EvaluationContext evaluationContext)
      throws InterruptedException;

  /** Same as {@link #delete(BiPredicate)}, but takes a predicate that only uses the key. */
  default void delete(Predicate<SkyKey> pred) {
    delete((k, v) -> pred.test(k));
  }

  /**
   * Ensures that after the next completed {@link #evaluate} call the current values of any value
   * matching this predicate (and all values that transitively depend on them) will be removed from
   * the value cache. All values that were already marked dirty in the graph will also be deleted,
   * regardless of whether or not they match the predicate.
   *
   * <p>If a later call to {@link #evaluate} requests some of the deleted values, those values will
   * be recomputed and the new values stored in the cache again.
   *
   * <p>To delete all dirty values, you can specify a predicate that's always false.
   */
  void delete(BiPredicate<SkyKey, SkyValue> pred);

  /**
   * Marks dirty values for deletion if they have been dirty for at least as many graph versions
   * as the specified limit.
   *
   * <p>This ensures that after the next completed {@link #evaluate} call, all such values, along
   * with all values that transitively depend on them, will be removed from the value cache. Values
   * that were marked dirty after the threshold version will not be affected by this call.
   *
   * <p>If a later call to {@link #evaluate} requests some of the deleted values, those values will
   * be recomputed and the new values stored in the cache again.
   *
   * <p>To delete all dirty values, you can specify 0 for the limit.
   */
  void deleteDirty(long versionAgeLimit);

  /**
   * Returns the values in the graph.
   *
   * <p>The returned map may be a live view of the graph.
   */
  // TODO(bazel-team): Replace all usages of getValues, getDoneValues, getExistingValue,
  // and getExistingErrorForTesting with usages of WalkableGraph. Changing the getValues usages
  // require some care because getValues gives access to the previous value for changed/dirty nodes.
  Map<SkyKey, SkyValue> getValues();

  /**
   * Returns an {@link InMemoryGraph} containing all of the nodes backing this evaluator.
   *
   * <p>Throws {@link UnsupportedOperationException} if this evaluator does not store its entire
   * graph in memory.
   */
  InMemoryGraph getInMemoryGraph();

  /**
   * Informs the evaluator that a sequence of evaluations at the same version has finished.
   * Evaluators may make optimizations under the assumption that successive evaluations are all at
   * the same version. A call of this method tells the evaluator that the next evaluation is not
   * guaranteed to be at the same version.
   */
  default void noteEvaluationsAtSameVersionMayBeFinished(ExtendedEventHandler eventHandler)
      throws InterruptedException {
    postLoggingStats(eventHandler);
  }

  /**
   * Tells the evaluator to post any logging statistics that it may have accumulated over the last
   * sequence of evaluations. Normally called internally by {@link
   * #noteEvaluationsAtSameVersionMayBeFinished}.
   */
  default void postLoggingStats(ExtendedEventHandler eventHandler) {}

  /**
   * Returns the done (without error) values in the graph.
   *
   * <p>The returned map may be a live view of the graph.
   */
  Map<SkyKey, SkyValue> getDoneValues();

  /**
   * Returns a value if and only if an earlier call to {@link #evaluate} created it; null otherwise.
   *
   * <p>This method should mainly be used by tests that need to verify the presence of a value in
   * the graph after an {@link #evaluate} call.
   */
  @Nullable
  SkyValue getExistingValue(SkyKey key) throws InterruptedException;

  @Nullable
  NodeEntry getExistingEntryAtCurrentlyEvaluatingVersion(SkyKey key) throws InterruptedException;

  /**
   * Returns an error if and only if an earlier call to {@link #evaluate} created it; null
   * otherwise.
   *
   * <p>This method should only be used by tests that need to verify the presence of an error in the
   * graph after an {@link #evaluate} call.
   */
  @SuppressWarnings("VisibleForTestingMisused") // Only exists for testing.
  @VisibleForTesting
  @Nullable
  ErrorInfo getExistingErrorForTesting(SkyKey key) throws InterruptedException;

  /**
   * Injects a {@link GraphTransformerForTesting} to allow tests to have finer-grained control over
   * the graph.
   *
   * <p>May be called multiple times, in which case the effective graph is the result of
   * sequentially applying all transformers in the order in which they were passed to this method.
   *
   * <p>Must only be called in tests.
   */
  void injectGraphTransformerForTesting(GraphTransformerForTesting transformer);

  /** Transforms a graph, possibly injecting other functionality. */
  interface GraphTransformerForTesting {
    InMemoryGraph transform(InMemoryGraph graph);

    ProcessableGraph transform(ProcessableGraph graph);

    GraphTransformerForTesting NO_OP =
        new GraphTransformerForTesting() {
          @Override
          public InMemoryGraph transform(InMemoryGraph graph) {
            return graph;
          }

          @Override
          public ProcessableGraph transform(ProcessableGraph graph) {
            return graph;
          }
        };

    /**
     * Returns a composite transformer that applies both of the given transformers in the given
     * order.
     */
    static GraphTransformerForTesting compose(
        GraphTransformerForTesting before, GraphTransformerForTesting after) {
      checkNotNull(before);
      checkNotNull(after);
      if (before == NO_OP) {
        return after;
      }
      if (after == NO_OP) {
        return before;
      }
      return new GraphTransformerForTesting() {
        @Override
        public InMemoryGraph transform(InMemoryGraph graph) {
          return after.transform(before.transform(graph));
        }

        @Override
        public ProcessableGraph transform(ProcessableGraph graph) {
          return after.transform(before.transform(graph));
        }
      };
    }
  }

  /**
   * Writes a brief summary about the graph to the given output stream.
   *
   * <p>Not necessarily thread-safe. Use only for debugging purposes.
   */
  @ThreadHostile
  void dumpSummary(PrintStream out);

  /**
   * Writes a list of counts of each node type in the graph to the given output stream.
   *
   * <p>Not necessarily thread-safe. Use only for debugging purposes.
   */
  @ThreadHostile
  void dumpCount(PrintStream out);

  /**
   * Writes a detailed summary of the graph to the given output stream. For each key matching the
   * given filter, prints the key name and value.
   *
   * <p>Not necessarily thread-safe. Use only for debugging purposes.
   */
  @ThreadHostile
  void dumpValues(PrintStream out, Predicate<String> filter) throws InterruptedException;

  /**
   * Writes a detailed summary of the graph to the given output stream. For each key matching the
   * given filter, prints the key name and deps. The deps are printed in groups according to the
   * dependency order registered in Skyframe.
   *
   * <p>Not necessarily thread-safe. Use only for debugging purposes.
   */
  @ThreadHostile
  void dumpDeps(PrintStream out, Predicate<String> filter) throws InterruptedException;

  /**
   * Emits the graph representation in the DOT description format of SkyFunction dependencies of the
   * keys matching the given filter to the given output stream.
   *
   * <p>Useful for understanding the high level dependency edges established by Skyframe lookups.
   * calls.
   *
   * <p>The nodes are {@link SkyFunctionName}s. They do not include individual SkyKey information
   * since the most basic builds already create way too many nodes to generate a useful graph image.
   *
   * <p>Edges are de-duplicated (e.g. all FILE -> FILE_STATE edges show up as a single edge), and
   * the output may show cycles (e.g. ACTION_EXECUTION -> ARTIFACT -> ACTION_EXECUTION -> ...)
   *
   * <p>Not necessarily thread-safe. Use only for debugging purposes.
   */
  @ThreadHostile
  void dumpFunctionGraph(PrintStream out, Predicate<String> filter) throws InterruptedException;

  /**
   * Writes a detailed summary of the graph to the given output stream. For each key matching the
   * given filter, prints the key name and its reverse deps.
   *
   * <p>Not necessarily thread-safe. Use only for debugging purposes.
   */
  @ThreadHostile
  void dumpRdeps(PrintStream out, Predicate<String> filter) throws InterruptedException;

  /**
   * Cleans up {@linkplain com.google.devtools.build.lib.concurrent.PooledInterner.Pool interning
   * pools} by moving objects to weak interners and uninstalling the current pools.
   *
   * <p>May destroy this evaluator's {@linkplain #getInMemoryGraph graph}. Only call when the graph
   * is about to be thrown away.
   */
  void cleanupInterningPools();

  boolean skyfocusSupported();

  /**
   * Enables Skyfocus, a graph optimizer for Skyframe with active directoriess, by remembering the
   * root nodes.
   */
  void rememberTopLevelEvaluations(boolean remember);

  /** Cleans up the set of evaluated root SkyKeys. Used for Skyfocus. */
  void cleanupLatestTopLevelEvaluations();
}
