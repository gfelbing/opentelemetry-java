/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.metrics.AsynchronousInstrument;
import io.opentelemetry.sdk.metrics.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.common.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.common.InstrumentValueType;
import io.opentelemetry.sdk.metrics.view.AggregationConfiguration;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.annotation.Nullable;

abstract class AbstractDoubleAsynchronousInstrumentBuilder<B extends AbstractInstrument.Builder<?>>
    extends AbstractInstrument.Builder<B> {
  private final MeterProviderSharedState meterProviderSharedState;
  private final MeterSharedState meterSharedState;
  @Nullable private Consumer<AsynchronousInstrument.DoubleResult> updater;

  AbstractDoubleAsynchronousInstrumentBuilder(
      String name,
      InstrumentType instrumentType,
      InstrumentValueType instrumentValueType,
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState) {
    super(name, instrumentType, instrumentValueType);
    this.meterProviderSharedState = meterProviderSharedState;
    this.meterSharedState = meterSharedState;
  }

  public B setUpdater(Consumer<AsynchronousInstrument.DoubleResult> updater) {
    this.updater = updater;
    return getThis();
  }

  final <I extends AbstractInstrument, T> I buildInstrument(
      BiFunction<InstrumentDescriptor, AsynchronousInstrumentAccumulator, I> instrumentFactory) {
    InstrumentDescriptor descriptor = buildDescriptor();
    AggregationConfiguration configuration =
        meterProviderSharedState.getViewRegistry().findView(descriptor);
    Aggregator<T> aggregator =
        configuration
            .getAggregatorFactory()
            .create(
                meterProviderSharedState.getResource(),
                meterSharedState.getInstrumentationLibraryInfo(),
                descriptor);
    return meterSharedState
        .getInstrumentRegistry()
        .register(
            instrumentFactory.apply(
                descriptor,
                AsynchronousInstrumentAccumulator.doubleAsynchronousAccumulator(
                    aggregator,
                    InstrumentProcessor.createProcessor(
                        aggregator,
                        meterProviderSharedState.getStartEpochNanos(),
                        configuration.getTemporality()),
                    updater)));
  }
}
