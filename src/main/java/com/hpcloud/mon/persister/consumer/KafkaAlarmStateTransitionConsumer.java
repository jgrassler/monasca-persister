package com.hpcloud.mon.persister.consumer;

import com.google.inject.Inject;
import com.hpcloud.mon.persister.configuration.MonPersisterConfiguration;
import kafka.consumer.KafkaStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KafkaAlarmStateTransitionConsumer {

    private static final String KAFKA_CONFIGURATION = "Kafka configuration:";
    private static final Logger logger = LoggerFactory.getLogger(KafkaAlarmStateTransitionConsumer.class);

    private final Integer numThreads;
    private ExecutorService executorService;
    private final KafkaAlarmStateTransitionConsumerRunnableBasicFactory kafkaConsumerRunnableBasicFactory;
    @Inject
    private KafkaStreams kafkaStreams;

    @Inject
    public KafkaAlarmStateTransitionConsumer(MonPersisterConfiguration configuration,
                                             KafkaAlarmStateTransitionConsumerRunnableBasicFactory kafkaConsumerRunnableBasicFactory) {

        this.numThreads = configuration.getKafkaConfiguration().getNumThreads();
        logger.info(KAFKA_CONFIGURATION + " numThreads = " + numThreads);

        this.kafkaConsumerRunnableBasicFactory = kafkaConsumerRunnableBasicFactory;
    }

    public void run() {
        List<KafkaStream<byte[], byte[]>> streams = kafkaStreams.getStreams().get("alarm-state-transitions");
        executorService = Executors.newFixedThreadPool(numThreads);

        int threadNumber = 0;
        for (final KafkaStream stream : streams) {
            executorService.submit(kafkaConsumerRunnableBasicFactory.create(stream, threadNumber));
            threadNumber++;
        }
    }

    public void stop() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}