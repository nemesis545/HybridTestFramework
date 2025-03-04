package com.eventing;

import com.avro.Customer;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

/**
 * @author Dipjyoti Metia
 */
@Slf4j
public class Kafka {

    private Properties getKafkaStreamsConfig(Config config) {
        // Create producer properties
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.ACKS_CONFIG, "1");
        properties.setProperty(ProducerConfig.RETRIES_CONFIG, "10");

        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapURL());
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        properties.setProperty("schema.registry.url", config.getSchemaRegistry());
        return properties;
    }

    private Properties setConsumerConfig(Config config) {
        Properties properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapURL());
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, config.getGroupID());
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // "earliest/latest/none"
        properties.setProperty("schema.registry.url", config.getSchemaRegistry());
        properties.setProperty("specific.avro.reader", "true");
        return properties;
    }

    /**
     * create topics
     * @param config config
     */
    public void createTopic(Config config) {
        // Create the producer
        KafkaProducer<String, String> producer = new KafkaProducer<>(config.getProperties());
        ProducerRecord<String, String> record = new ProducerRecord<>(config.getTopic(), config.getData());
        System.out.println("Creating producer");
        // Send Data
        producer.send(record, (metadata, e) -> {
            // Execute every time record is successfully send
            if (e == null) {
                log.info(String.valueOf(metadata.timestamp()));
                log.info(config.getTopic(), metadata.topic());
                log.info(String.valueOf(metadata.hasOffset()));
                log.info(String.valueOf(metadata.hasTimestamp()));
            } else {
                e.printStackTrace();
            }
        });
        producer.flush();
        producer.close();
    }

    /**
     * Produce kafka messages
     *
     * @param config
     * @throws Exception
     */
    public void producer(Config config) throws Exception {
        Properties configProperty = getKafkaStreamsConfig(config);
        KafkaProducer<String, Customer> kafkaProducer = new KafkaProducer<>(configProperty);

        for (int i = 0; i < 10; i++) {
            Thread.sleep(1000);
            Customer customer = Customer.newBuilder()
                    .setFirstName("John")
                    .setLastName("Doe")
                    .setAge(25)
                    .setHeight(185.5f)
                    .setWeight(85.6f)
                    .setAutomatedEmail(false)
                    .build();

            ProducerRecord<String, Customer> producerRecord = new ProducerRecord<>(
                    config.getTopic(), customer
            );

            kafkaProducer.send(producerRecord, (metadata, exception) -> {
                if (exception == null) {
                    System.out.println("Success");
                    System.out.println(metadata.toString());
                } else {
                    exception.printStackTrace();
                }
            });
        }
        kafkaProducer.flush();
        kafkaProducer.close();
    }

    /**
     * Consume kafka messages
     *
     * @param config
     */
    public void consumer(Config config) {
        Properties configProperties = setConsumerConfig(config);
        try (KafkaConsumer<String, Customer> kafkaConsumer = new KafkaConsumer<>(configProperties)) {
            kafkaConsumer.subscribe(Collections.singleton(config.getTopic()));
            log.info("Waiting for data");
            while (true) {
                ConsumerRecords<String, Customer> records = kafkaConsumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, Customer> record : records) {
                    Customer customer = record.value();
                    System.out.println(customer);
                }
                kafkaConsumer.commitSync();
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
//        kafkaConsumer.close();
    }
}
