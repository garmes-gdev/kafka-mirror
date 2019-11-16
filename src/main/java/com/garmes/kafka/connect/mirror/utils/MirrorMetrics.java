/*
 * kafka-connect-mirror - Apache Kafka connector to mirror data
 *
 * Copyright (c) 2018, Mohammed Amine GARMES
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.garmes.kafka.connect.mirror.utils;

import com.garmes.kafka.connect.mirror.MirrorSourceConnector;
import com.garmes.kafka.connect.mirror.MirrorSourceTaskConfig;
import org.apache.kafka.common.MetricNameTemplate;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.apache.kafka.common.metrics.Sensor;
import org.apache.kafka.common.metrics.stats.Count;
import org.apache.kafka.common.metrics.stats.Value;
import org.apache.kafka.common.metrics.stats.Rate;
import org.apache.kafka.common.metrics.stats.Min;
import org.apache.kafka.common.metrics.stats.Max;
import org.apache.kafka.common.metrics.stats.Avg;
import org.apache.kafka.common.TopicPartition;

import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;


public class MirrorMetrics {

    private static final String SOURCE_CONNECTOR_GROUP = MirrorSourceConnector.class.getSimpleName();
    private static final Set<String> PARTITION_TAGS = new HashSet<>(Arrays.asList("topic", "partition"));

    private static final MetricNameTemplate RECORD_COUNT = new MetricNameTemplate(
            "record-count", SOURCE_CONNECTOR_GROUP,
            "Number of source records replicated to the target cluster.", PARTITION_TAGS);
    private static final MetricNameTemplate RECORD_AGE = new MetricNameTemplate(
            "record-age-ms", SOURCE_CONNECTOR_GROUP,
            "The age of incoming source records when replicated to the target cluster.", PARTITION_TAGS);
    private static final MetricNameTemplate RECORD_AGE_MAX = new MetricNameTemplate(
            "record-age-ms-max", SOURCE_CONNECTOR_GROUP,
            "The age of incoming source records when replicated to the target cluster.", PARTITION_TAGS);
    private static final MetricNameTemplate RECORD_AGE_MIN = new MetricNameTemplate(
            "record-age-ms-min", SOURCE_CONNECTOR_GROUP,
            "The age of incoming source records when replicated to the target cluster.", PARTITION_TAGS);
    private static final MetricNameTemplate RECORD_AGE_AVG = new MetricNameTemplate(
            "record-age-ms-avg", SOURCE_CONNECTOR_GROUP,
            "The age of incoming source records when replicated to the target cluster.", PARTITION_TAGS);
    private static final MetricNameTemplate BYTE_RATE = new MetricNameTemplate(
            "byte-rate", SOURCE_CONNECTOR_GROUP,
            "Average number of bytes replicated per second.", PARTITION_TAGS);
    private static final MetricNameTemplate REPLICATION_LATENCY = new MetricNameTemplate(
            "replication-latency-ms", SOURCE_CONNECTOR_GROUP,
            "Time it takes records to replicate from source to target cluster.", PARTITION_TAGS);
    private static final MetricNameTemplate REPLICATION_LATENCY_MAX = new MetricNameTemplate(
            "replication-latency-ms-max", SOURCE_CONNECTOR_GROUP,
            "Time it takes records to replicate from source to target cluster.", PARTITION_TAGS);
    private static final MetricNameTemplate REPLICATION_LATENCY_MIN = new MetricNameTemplate(
            "replication-latency-ms-min", SOURCE_CONNECTOR_GROUP,
            "Time it takes records to replicate from source to target cluster.", PARTITION_TAGS);
    private static final MetricNameTemplate REPLICATION_LATENCY_AVG = new MetricNameTemplate(
            "replication-latency-ms-avg", SOURCE_CONNECTOR_GROUP,
            "Time it takes records to replicate from source to target cluster.", PARTITION_TAGS);


    private final Metrics metrics;
    private final Map<TopicPartition, PartitionMetrics> partitionMetrics;


    public MirrorMetrics(MirrorSourceTaskConfig taskConfig) {
        this.metrics = new Metrics();

        metrics.sensor("record-count");
        metrics.sensor("byte-rate");
        metrics.sensor("record-age");
        metrics.sensor("replication-latency");

        String format = taskConfig.getTopicRenameFormat();

        partitionMetrics =  taskConfig.getPartitions().stream()
                .map(x -> new TopicPartition(ConnectHelper.renameTopic(format,x.topic()), x.partition()))
                .collect(Collectors.toMap(x -> x, PartitionMetrics::new));

    }

    public void countRecord(TopicPartition topicPartition) {
        partitionMetrics.get(topicPartition).recordSensor.record();
    }

    public void recordAge(TopicPartition topicPartition, long ageMillis) {
        partitionMetrics.get(topicPartition).recordAgeSensor.record((double) ageMillis);
    }

    public void replicationLatency(TopicPartition topicPartition, long millis) {
        partitionMetrics.get(topicPartition).replicationLatencySensor.record((double) millis);
    }

    public void recordBytes(TopicPartition topicPartition, long bytes) {
        partitionMetrics.get(topicPartition).byteRateSensor.record((double) bytes);
    }

    public void addReporter(MetricsReporter reporter) {
        metrics.addReporter(reporter);
    }

    private class PartitionMetrics {
        private final Sensor recordSensor;
        private final Sensor byteRateSensor;
        private final Sensor recordAgeSensor;
        private final Sensor replicationLatencySensor;

        PartitionMetrics(TopicPartition topicPartition) {
            Map<String, String> tags = new LinkedHashMap<>();
            tags.put("topic", topicPartition.topic());
            tags.put("partition", Integer.toString(topicPartition.partition()));

            recordSensor = metrics.sensor("record-count");
            recordSensor.add(metrics.metricInstance(RECORD_COUNT, tags), new Count());

            byteRateSensor = metrics.sensor("byte-rate");
            byteRateSensor.add(metrics.metricInstance(BYTE_RATE, tags), new Rate());

            recordAgeSensor = metrics.sensor("record-age");
            recordAgeSensor.add(metrics.metricInstance(RECORD_AGE, tags), new Value());
            recordAgeSensor.add(metrics.metricInstance(RECORD_AGE_MAX, tags), new Max());
            recordAgeSensor.add(metrics.metricInstance(RECORD_AGE_MIN, tags), new Min());
            recordAgeSensor.add(metrics.metricInstance(RECORD_AGE_AVG, tags), new Avg());

            replicationLatencySensor = metrics.sensor("replication-latency");
            replicationLatencySensor.add(metrics.metricInstance(REPLICATION_LATENCY, tags), new Value());
            replicationLatencySensor.add(metrics.metricInstance(REPLICATION_LATENCY_MAX, tags), new Max());
            replicationLatencySensor.add(metrics.metricInstance(REPLICATION_LATENCY_MIN, tags), new Min());
            replicationLatencySensor.add(metrics.metricInstance(REPLICATION_LATENCY_AVG, tags), new Avg());
        }
    }

}