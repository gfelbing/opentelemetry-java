/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.aggregator;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.opentelemetry.api.common.Labels;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.common.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.data.DoubleSummaryData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
final class LongMinMaxSumCountAggregator extends AbstractAggregator<MinMaxSumCountAccumulation> {
  LongMinMaxSumCountAggregator(
      Resource resource,
      InstrumentationLibraryInfo instrumentationLibraryInfo,
      InstrumentDescriptor descriptor) {
    super(resource, instrumentationLibraryInfo, descriptor);
  }

  @Override
  public AggregatorHandle<MinMaxSumCountAccumulation> createHandle() {
    return new Handle();
  }

  @Override
  public MinMaxSumCountAccumulation accumulateLong(long value) {
    return MinMaxSumCountAccumulation.create(1, value, value, value);
  }

  @Override
  public MinMaxSumCountAccumulation merge(
      MinMaxSumCountAccumulation a1, MinMaxSumCountAccumulation a2) {
    return MinMaxSumCountAccumulation.create(
        a1.getCount() + a2.getCount(),
        a1.getSum() + a2.getSum(),
        Math.min(a1.getMin(), a2.getMin()),
        Math.max(a1.getMax(), a2.getMax()));
  }

  @Override
  public MetricData toMetricData(
      Map<Labels, MinMaxSumCountAccumulation> accumulationByLabels,
      long startEpochNanos,
      long epochNanos) {
    return MetricData.createDoubleSummary(
        getResource(),
        getInstrumentationLibraryInfo(),
        getInstrumentDescriptor().getName(),
        getInstrumentDescriptor().getDescription(),
        getInstrumentDescriptor().getUnit(),
        DoubleSummaryData.create(
            MetricDataUtils.toDoubleSummaryPointList(
                accumulationByLabels, startEpochNanos, epochNanos)));
  }

  static final class Handle extends AggregatorHandle<MinMaxSumCountAccumulation> {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    // The current value. This controls its own internal thread-safety via method access. Don't
    // try to use its fields directly.
    @GuardedBy("lock")
    private final LongState current = new LongState();

    @Override
    protected MinMaxSumCountAccumulation doAccumulateThenReset() {
      lock.writeLock().lock();
      try {
        MinMaxSumCountAccumulation toReturn =
            MinMaxSumCountAccumulation.create(current.count, current.sum, current.min, current.max);
        current.reset();
        return toReturn;
      } finally {
        lock.writeLock().unlock();
      }
    }

    @Override
    protected void doRecordLong(long value) {
      lock.writeLock().lock();
      try {
        current.record(value);
      } finally {
        lock.writeLock().unlock();
      }
    }

    private static final class LongState {
      private long count;
      private long sum;
      private long min;
      private long max;

      public LongState() {
        reset();
      }

      private void reset() {
        this.sum = 0;
        this.count = 0;
        this.min = Long.MAX_VALUE;
        this.max = Long.MIN_VALUE;
      }

      public void record(long value) {
        count++;
        sum += value;
        min = Math.min(value, min);
        max = Math.max(value, max);
      }
    }
  }
}
