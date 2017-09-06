/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.soundcloud.prometheus.hystrix;

import com.netflix.hystrix.*;
import com.netflix.hystrix.metric.HystrixCommandCompletion;
import com.netflix.hystrix.metric.HystrixCommandCompletionStream;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import rx.functions.Action1;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * Implementation of a {@link HystrixMetricsPublisherCommand} for Prometheus Metrics.
 * See <a href="https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring">Hystrix Metrics and Monitoring</a>.
 */
public class HystrixPrometheusMetricsPublisherCommand implements HystrixMetricsPublisherCommand {

    private final Map<String, String> labels;
    private final boolean exportProperties;

    private final HystrixCommandMetrics metrics;
    private final HystrixCircuitBreaker circuitBreaker;
    private final HystrixCommandProperties properties;
    private final HystrixMetricsCollector collector;
    private final HystrixCommandKey commandKey;
    private HystrixMetricsPublisherCommand delegate;
    private boolean exportDeprecatedMetrics;

    public HystrixPrometheusMetricsPublisherCommand(
            HystrixMetricsCollector collector, HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey,
            HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker,
            HystrixCommandProperties properties, boolean exportProperties, boolean exportDeprecatedMetrics,
            HystrixMetricsPublisherCommand delegate) {

        this.labels = new TreeMap<>();
        this.labels.put("command_group", (commandGroupKey != null) ? commandGroupKey.name() : "default");
        this.labels.put("command_name", commandKey.name());

        this.exportProperties = exportProperties;

        this.circuitBreaker = circuitBreaker;
        this.properties = properties;
        this.collector = collector;
        this.metrics = metrics;
        this.commandKey = commandKey;
        this.delegate = delegate;
        this.exportDeprecatedMetrics = exportDeprecatedMetrics;
    }

    @Override
    public void initialize() {
        delegate.initialize();

        String circuitDoc = "Current status of circuit breaker: 1 = open, 0 = closed.";
        addGauge("is_circuit_breaker_open", circuitDoc, new Callable<Number>() {
            @Override
            public Number call() throws Exception {
                return HystrixPrometheusMetricsPublisherCommand.this.booleanToNumber(HystrixPrometheusMetricsPublisherCommand.this.circuitBreaker.isOpen());
            }
        });

        String permitsDoc = "The number of executionSemaphorePermits in use right now.";
        addGauge("execution_semaphore_permits_in_use", permitsDoc, new Callable<Number>() {
            @Override
            public Number call() throws Exception {
                return HystrixPrometheusMetricsPublisherCommand.this.metrics.getCurrentConcurrentExecutionCount();
            }
        });

        if (exportDeprecatedMetrics) {
            String errorsDoc = "Error percentage derived from current metrics.";
            addGauge("error_percentage", "DEPRECATED: " + errorsDoc, new Callable<Number>() {
                @Override
                public Number call() throws Exception {
                    return HystrixPrometheusMetricsPublisherCommand.this.metrics.getHealthCounts().getErrorPercentage();
                }
            });

            createCumulativeCountForEvent("count_emit", HystrixRollingNumberEvent.EMIT);
            createCumulativeCountForEvent("count_success", HystrixRollingNumberEvent.SUCCESS);
            createCumulativeCountForEvent("count_failure", HystrixRollingNumberEvent.FAILURE);
            createCumulativeCountForEvent("count_timeout", HystrixRollingNumberEvent.TIMEOUT);
            createCumulativeCountForEvent("count_bad_requests", HystrixRollingNumberEvent.BAD_REQUEST);
            createCumulativeCountForEvent("count_short_circuited", HystrixRollingNumberEvent.SHORT_CIRCUITED);
            createCumulativeCountForEvent("count_thread_pool_rejected", HystrixRollingNumberEvent.THREAD_POOL_REJECTED);
            createCumulativeCountForEvent("count_semaphore_rejected", HystrixRollingNumberEvent.SEMAPHORE_REJECTED);
            createCumulativeCountForEvent("count_fallback_emit", HystrixRollingNumberEvent.FALLBACK_EMIT);
            createCumulativeCountForEvent("count_fallback_success", HystrixRollingNumberEvent.FALLBACK_SUCCESS);
            createCumulativeCountForEvent("count_fallback_failure", HystrixRollingNumberEvent.FALLBACK_FAILURE);
            createCumulativeCountForEvent("count_fallback_rejection", HystrixRollingNumberEvent.FALLBACK_REJECTION);
            createCumulativeCountForEvent("count_exceptions_thrown", HystrixRollingNumberEvent.EXCEPTION_THROWN);
            createCumulativeCountForEvent("count_responses_from_cache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);
            createCumulativeCountForEvent("count_collapsed_requests", HystrixRollingNumberEvent.COLLAPSED);

            createRollingCountForEvent("rolling_count_emit", HystrixRollingNumberEvent.EMIT);
            createRollingCountForEvent("rolling_count_success", HystrixRollingNumberEvent.SUCCESS);
            createRollingCountForEvent("rolling_count_failure", HystrixRollingNumberEvent.FAILURE);
            createRollingCountForEvent("rolling_count_timeout", HystrixRollingNumberEvent.TIMEOUT);
            createRollingCountForEvent("rolling_count_bad_requests", HystrixRollingNumberEvent.BAD_REQUEST);
            createRollingCountForEvent("rolling_count_short_circuited", HystrixRollingNumberEvent.SHORT_CIRCUITED);
            createRollingCountForEvent("rolling_count_thread_pool_rejected", HystrixRollingNumberEvent.THREAD_POOL_REJECTED);
            createRollingCountForEvent("rolling_count_semaphore_rejected", HystrixRollingNumberEvent.SEMAPHORE_REJECTED);
            createRollingCountForEvent("rolling_count_fallback_emit", HystrixRollingNumberEvent.FALLBACK_EMIT);
            createRollingCountForEvent("rolling_count_fallback_success", HystrixRollingNumberEvent.FALLBACK_SUCCESS);
            createRollingCountForEvent("rolling_count_fallback_failure", HystrixRollingNumberEvent.FALLBACK_FAILURE);
            createRollingCountForEvent("rolling_count_fallback_rejection", HystrixRollingNumberEvent.FALLBACK_REJECTION);
            createRollingCountForEvent("rolling_count_exceptions_thrown", HystrixRollingNumberEvent.EXCEPTION_THROWN);
            createRollingCountForEvent("rolling_count_responses_from_cache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);
            createRollingCountForEvent("rolling_count_collapsed_requests", HystrixRollingNumberEvent.COLLAPSED);

            String latencyExecDoc = "DEPRECATED: Rolling percentiles of execution times for the "
                    + "HystrixCommand.run() method (on the child thread if using thread isolation).";

            addGauge("latency_execute_mean", latencyExecDoc, new Callable<Number>() {
                @Override
                public Number call() throws Exception {
                    return HystrixPrometheusMetricsPublisherCommand.this.metrics.getExecutionTimeMean();
                }
            });

            createExcecutionTimePercentile("latency_execute_percentile_5", 5, latencyExecDoc);
            createExcecutionTimePercentile("latency_execute_percentile_25", 25, latencyExecDoc);
            createExcecutionTimePercentile("latency_execute_percentile_50", 50, latencyExecDoc);
            createExcecutionTimePercentile("latency_execute_percentile_75", 75, latencyExecDoc);
            createExcecutionTimePercentile("latency_execute_percentile_90", 90, latencyExecDoc);
            createExcecutionTimePercentile("latency_execute_percentile_99", 99, latencyExecDoc);
            createExcecutionTimePercentile("latency_execute_percentile_995", 99.5, latencyExecDoc);

            String latencyTotalDoc = "DEPRECATED: Rolling percentiles of execution times for the "
                    + "end-to-end execution of HystrixCommand.execute() or HystrixCommand.queue() until "
                    + "a response is returned (or ready to return in case of queue(). The purpose of this "
                    + "compared with the latency_execute* percentiles is to measure the cost of thread "
                    + "queuing/scheduling/execution, semaphores, circuit breaker logic and other "
                    + "aspects of overhead (including metrics capture itself).";

            addGauge("latency_total_mean", latencyTotalDoc, new Callable<Number>() {
                @Override
                public Number call() throws Exception {
                    return HystrixPrometheusMetricsPublisherCommand.this.metrics.getTotalTimeMean();
                }
            });

            createTotalTimePercentile("latency_total_percentile_5", 5, latencyTotalDoc);
            createTotalTimePercentile("latency_total_percentile_25", 25, latencyTotalDoc);
            createTotalTimePercentile("latency_total_percentile_50", 50, latencyTotalDoc);
            createTotalTimePercentile("latency_total_percentile_75", 75, latencyTotalDoc);
            createTotalTimePercentile("latency_total_percentile_90", 90, latencyTotalDoc);
            createTotalTimePercentile("latency_total_percentile_99", 99, latencyTotalDoc);
            createTotalTimePercentile("latency_total_percentile_995", 99.5, latencyTotalDoc);
        }

        String histogramLatencyTotalDoc = "Execution times for the "
                + "end-to-end execution of HystrixCommand.execute() or HystrixCommand.queue() until "
                + "a response is returned (or ready to return in case of queue(). The purpose of this "
                + "compared with the latency_execute* percentiles is to measure the cost of thread "
                + "queuing/scheduling/execution, semaphores, circuit breaker logic and other "
                + "aspects of overhead (including metrics capture itself).";

        String histogramLatencyExecDoc = "Execution times for the "
                + "HystrixCommand.run() method (on the child thread if using thread isolation).";

        final Histogram.Child histogramLatencyTotal = addHistogram("latency_total_seconds", histogramLatencyTotalDoc);
        final Histogram.Child histogramLatencyExecute = addHistogram("latency_execute_seconds", histogramLatencyExecDoc);

        // pre-allocate the counters for all possible events to have a clean start for the metrics
        final HashMap<HystrixEventType, Counter.Child> eventCounters = new HashMap<>();
        for (HystrixEventType hystrixEventType : HystrixEventType.values()) {
            eventCounters.put(hystrixEventType, addCounter("event_total", "event", hystrixEventType.name().toLowerCase()));
        }
        final Counter.Child totalCounter = addCounter("total");
        final Counter.Child errorCounter = addCounter("error_total");

        HystrixCommandCompletionStream.getInstance(commandKey)
                .observe()
                .subscribe(new Action1<HystrixCommandCompletion>() {
                    @Override
                    public void call(HystrixCommandCompletion hystrixCommandCompletion) {
						histogramLatencyTotal.observe(hystrixCommandCompletion.getTotalLatency() / 1000d);
						histogramLatencyExecute.observe(hystrixCommandCompletion.getExecutionLatency() / 1000d);
                        for (HystrixEventType hystrixEventType : HystrixEventType.values()) {
                            int count = hystrixCommandCompletion.getEventCounts().getCount(hystrixEventType);
                            if (count > 0) {
                                switch (hystrixEventType) {
                                    /** this list is derived from {@link HystrixCommandMetrics.HealthCounts.plus} */
                                    case FAILURE:
                                    case TIMEOUT:
                                    case THREAD_POOL_REJECTED:
                                    case SEMAPHORE_REJECTED:
                                        errorCounter.inc(count);
                                    case SUCCESS:
                                        totalCounter.inc(count);
                                        break;
                                }
                                eventCounters.get(hystrixEventType).inc(count);
                            }
                        }
                    }
                });

        if (exportProperties) {
            String propDesc = "These informational metrics report the "
                    + "actual property values being used by the HystrixCommand. This is useful to "
                    + "see when a dynamic property takes effect and confirm a property is set as "
                    + "expected.";

            addGauge("property_value_rolling_statistical_window_in_milliseconds", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return HystrixPrometheusMetricsPublisherCommand.this.properties.metricsRollingStatisticalWindowInMilliseconds().get();
                        }
                    });

            addGauge("property_value_circuit_breaker_request_volume_threshold", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return HystrixPrometheusMetricsPublisherCommand.this.properties.circuitBreakerRequestVolumeThreshold().get();
                        }
                    });

            addGauge("property_value_circuit_breaker_sleep_window_in_milliseconds", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return HystrixPrometheusMetricsPublisherCommand.this.properties.circuitBreakerSleepWindowInMilliseconds().get();
                        }
                    });

            addGauge("property_value_circuit_breaker_error_threshold_percentage", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return HystrixPrometheusMetricsPublisherCommand.this.properties.circuitBreakerErrorThresholdPercentage().get();
                        }
                    });

            addGauge("property_value_circuit_breaker_force_open", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return HystrixPrometheusMetricsPublisherCommand.this.booleanToNumber(HystrixPrometheusMetricsPublisherCommand.this.properties.circuitBreakerForceOpen().get());
                        }
                    });

            addGauge("property_value_circuit_breaker_force_closed", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return HystrixPrometheusMetricsPublisherCommand.this.booleanToNumber(HystrixPrometheusMetricsPublisherCommand.this.properties.circuitBreakerForceClosed().get());
                        }
                    });

            addGauge("property_value_execution_timeout_in_milliseconds", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return HystrixPrometheusMetricsPublisherCommand.this.properties.executionTimeoutInMilliseconds().get();
                        }
                    });

            addGauge("property_value_execution_isolation_strategy", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return HystrixPrometheusMetricsPublisherCommand.this.properties.executionIsolationStrategy().get().ordinal();
                        }
                    });

            addGauge("property_value_metrics_rolling_percentile_enabled", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return HystrixPrometheusMetricsPublisherCommand.this.booleanToNumber(HystrixPrometheusMetricsPublisherCommand.this.properties.metricsRollingPercentileEnabled().get());
                        }
                    });

            addGauge("property_value_request_cache_enabled", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return HystrixPrometheusMetricsPublisherCommand.this.booleanToNumber(HystrixPrometheusMetricsPublisherCommand.this.properties.requestCacheEnabled().get());
                        }
                    });

            addGauge("property_value_request_log_enabled", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return HystrixPrometheusMetricsPublisherCommand.this.booleanToNumber(HystrixPrometheusMetricsPublisherCommand.this.properties.requestLogEnabled().get());
                        }
                    });

            addGauge("property_value_execution_isolation_semaphore_max_concurrent_requests", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return HystrixPrometheusMetricsPublisherCommand.this.properties.executionIsolationSemaphoreMaxConcurrentRequests().get();
                        }
                    });

            addGauge("property_value_fallback_isolation_semaphore_max_concurrent_requests", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return HystrixPrometheusMetricsPublisherCommand.this.properties.fallbackIsolationSemaphoreMaxConcurrentRequests().get();
                        }
                    });
        }
    }

    private void createCumulativeCountForEvent(String name, final HystrixRollingNumberEvent event) {
        String doc = "DEPRECATED: These are cumulative counts since the start of the application.";
        addGauge(name, doc, new Callable<Number>() {
            @Override
            public Number call() throws Exception {
                return HystrixPrometheusMetricsPublisherCommand.this.metrics.getCumulativeCount(event);
            }
        });
    }

    private void createRollingCountForEvent(String name, final HystrixRollingNumberEvent event) {
        String doc = "DEPRECATED: These are \"point in time\" counts representing the last X seconds.";
        addGauge(name, doc, new Callable<Number>() {
            @Override
            public Number call() throws Exception {
                return HystrixPrometheusMetricsPublisherCommand.this.metrics.getRollingCount(event);
            }
        });
    }

    private void createExcecutionTimePercentile(String name, final double percentile, String documentation) {
        addGauge(name, documentation, new Callable<Number>() {
            @Override
            public Number call() throws Exception {
                return HystrixPrometheusMetricsPublisherCommand.this.metrics.getExecutionTimePercentile(percentile);
            }
        });
    }

    private void createTotalTimePercentile(String name, final double percentile, String documentation) {
        addGauge(name, documentation, new Callable<Number>() {
            @Override
            public Number call() throws Exception {
                return HystrixPrometheusMetricsPublisherCommand.this.metrics.getTotalTimePercentile(percentile);
            }
        });
    }

    private void addGauge(String metric, String helpDoc, Callable<Number> value) {
        collector.addGauge("hystrix_command", metric, helpDoc, labels, value);
    }

    private Histogram.Child addHistogram(String metric, String helpDoc) {
        return collector.addHistogram("hystrix_command", metric, helpDoc, labels);
    }

    private Counter.Child addCounter(String metric, String... additionalLabels) {
        Map<String, String> labels = new TreeMap<>(this.labels);
        labels.putAll(this.labels);
        Iterator<String> l = Arrays.asList(additionalLabels).iterator();
        while (l.hasNext()) {
            labels.put(l.next(), l.next());
        }
        return collector.addCounter("hystrix_command", metric,
                "These are cumulative counts since the start of the application.", labels);
    }

    private int booleanToNumber(boolean value) {
        return value ? 1 : 0;
    }
}
