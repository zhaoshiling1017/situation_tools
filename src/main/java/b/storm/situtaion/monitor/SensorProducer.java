package b.storm.situtaion.monitor;

import com.sensor.model.Sensor;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class SensorProducer extends Thread {

    private static AtomicLong i = new AtomicLong(0);
    private final KafkaProducer<String, byte[]> producer;
    private final String topic;

    public SensorProducer(String topic) {
        Properties kafkaProducerProperties = new Properties();
        kafkaProducerProperties.put("bootstrap.servers", "bd03.guiyang.lgy:9092");
        kafkaProducerProperties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProducerProperties.put("value.serializer","org.apache.kafka.common.serialization.ByteArraySerializer");
        kafkaProducerProperties.put("batch.size", 1);
        kafkaProducerProperties.put("linger.ms", 1);
        kafkaProducerProperties.put("buffer.memory", 33554432);
        kafkaProducerProperties.put("acks", "0");
        kafkaProducerProperties.put("topic.properties.fetch.enable", "true");
        producer=new KafkaProducer<String, byte[]>(kafkaProducerProperties);
        this.topic = topic;
    }
    public void run() {
        try {
            while (i.incrementAndGet() <= 1000000) {
                producer.send(new ProducerRecord<String, byte[]>(topic, null, getPBBytes()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static byte[] getPBBytes() {
        Sensor.SENSOR_LOG.Builder sensorLogBuilder = Sensor.SENSOR_LOG.newBuilder();

        Sensor.DNS.Builder builder = Sensor.DNS.newBuilder();
        builder.setDip("114.114.114.114");
        builder.setDport(1);
        builder.setSerialNum("serial_num");
        builder.setSport(1);
        builder.setAccessTime("aaa");
        builder.setDnsType(1);
        builder.setHost("host");
        Sensor.DNS dns = builder.build();

        sensorLogBuilder.setSkyeyeDns(dns);
        sensorLogBuilder.setMessageType(2);
        Sensor.SENSOR_LOG sensorLog = sensorLogBuilder.build();

        return sensorLog.toByteArray();
    }

    public static void main(String[] args) {
        ExecutorService service = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 10; i++) {
            service.execute(new SensorProducer("ty_dns"));
        }
    }
}
