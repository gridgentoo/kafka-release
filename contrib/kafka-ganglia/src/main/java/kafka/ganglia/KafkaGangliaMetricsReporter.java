/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kafka.ganglia;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricPredicate;
import com.yammer.metrics.reporting.GangliaReporter;

import kafka.metrics.KafkaMetricsConfig;
import kafka.metrics.KafkaMetricsReporter;
import kafka.metrics.KafkaMetricsReporterMBean;
import kafka.utils.VerifiableProperties;

public class KafkaGangliaMetricsReporter implements KafkaMetricsReporter,
	KafkaGangliaMetricsReporterMBean {

    static Logger LOG = Logger.getLogger(KafkaGangliaMetricsReporter.class);
    static String GANGLIA_DEFAULT_HOST = "localhost";
    static int GANGLIA_DEFAULT_PORT = 8649;
    static String GANGLIA_DEFAULT_PREFIX = "kafka";

    boolean initialized = false;
    boolean running = false;
    GangliaReporter reporter = null;
    String gangliaHost = GANGLIA_DEFAULT_HOST;
    int gangliaPort = GANGLIA_DEFAULT_PORT;
    String gangliaGroupPrefix = GANGLIA_DEFAULT_PREFIX;
    MetricPredicate predicate = MetricPredicate.ALL;

    @Override
    public String getMBeanName() {
        return "kafka:type=com.criteo.kafka.KafkaGangliaMetricsReporter";
    }

    @Override
    public synchronized void startReporter(long pollingPeriodSecs) {
        if (initialized && !running) {
            reporter.start(pollingPeriodSecs, TimeUnit.SECONDS);
            running = true;
            LOG.info(String.format("Started Kafka Ganglia metrics reporter with polling period %d seconds", pollingPeriodSecs));
        }
    }

    @Override
    public synchronized void stopReporter() {
        if (initialized && running) {
            reporter.shutdown();
            running = false;
            LOG.info("Stopped Kafka Ganglia metrics reporter");
            try {
                reporter = new GangliaReporter(
                                               Metrics.defaultRegistry(),
                                               gangliaHost,
                                               gangliaPort,
                                               gangliaGroupPrefix,
                                               predicate
                                               );
            } catch (IOException e) {
                LOG.error("Unable to initialize GangliaReporter", e);
            }
        }
    }

    @Override
    public synchronized void init(VerifiableProperties props) {
        if (!initialized) {
            KafkaMetricsConfig metricsConfig = new KafkaMetricsConfig(props);
            gangliaHost = props.getString("kafka.ganglia.metrics.host", GANGLIA_DEFAULT_HOST);
            gangliaPort = props.getInt("kafka.ganglia.metrics.port", GANGLIA_DEFAULT_PORT);
            gangliaGroupPrefix = props.getString("kafka.ganglia.metrics.group", GANGLIA_DEFAULT_PREFIX);
            String regex = props.getString("kafka.ganglia.metrics.exclude.regex", null);
            if (regex != null) {
                predicate = new RegexMetricPredicate(regex);
            }
            try {
                reporter = new GangliaReporter(
                                               Metrics.defaultRegistry(),
                                               gangliaHost,
                                               gangliaPort,
                                               gangliaGroupPrefix,
                                               predicate
                                               );
            } catch (IOException e) {
                LOG.error("Unable to initialize GangliaReporter", e);
            }
            if (props.getBoolean("kafka.ganglia.metrics.reporter.enabled", false)) {
                initialized = true;
                startReporter(metricsConfig.pollingIntervalSecs());
            }
        }
    }
}
