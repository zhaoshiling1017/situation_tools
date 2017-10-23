package b.storm.situtaion.monitor;

import b.storm.situtaion.utils.Geoip;
import b.storm.situtaion.utils.Geoip.Result;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import com.sensor.model.Sensor;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.googlecode.protobuf.format.JsonFormat;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;


/**
 * 富化ip信息
 * 
 * @author peter
 *
 */
public class IpEnrichmentSolt extends BaseRichBolt {

	private static final Logger log =  Logger.getLogger(IpEnrichmentSolt.class);
	private static Properties kafkaProducerProperties;
	private OutputCollector outputCollector;
	private static KafkaProducer<String, byte[]> producer;
	private static Properties pro = new Properties();
	static {
		try {
			//加载配置文件
			InputStream input = IpEnrichmentTopology.class.getResourceAsStream("/app.properties");
			pro.load(input);
			kafkaProducerProperties = new Properties();
			// 发送avro数据到kafka
			kafkaProducerProperties.put("bootstrap.servers",pro.getProperty("broker_url"));
			kafkaProducerProperties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
			kafkaProducerProperties.put("value.serializer","org.apache.kafka.common.serialization.ByteArraySerializer");
			kafkaProducerProperties.put("batch.size", 1);
			kafkaProducerProperties.put("linger.ms", 1);
			kafkaProducerProperties.put("buffer.memory", 33554432);
			kafkaProducerProperties.put("acks", "0");
			kafkaProducerProperties.put("topic.properties.fetch.enable", "true");
			producer=new KafkaProducer<String, byte[]>(kafkaProducerProperties);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("kafka producer初始化失败");
		}
	}
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		try {
			this.outputCollector = collector;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public void execute(Tuple tuple) {
		JsonElement jsonElement = null;
		// String tcpFlowInfo = (String) tuple.getValue(0);
		byte[] tcpFlowBytes = (byte[]) tuple.getValue(0);
		try {
			// 查找ip相关的信息
			// if (StringUtils.isNotBlank(tcpFlowInfo)) {
			if (null != tcpFlowBytes && tcpFlowBytes.length > 0) {
				log.error("ip data count " + Geoip.getInstance().data.size());
				// log.error("tcpFlowInfo------------:" + tcpFlowInfo);
				log.error("-------" + new String(tcpFlowBytes, "utf-8"));
				Sensor.SENSOR_LOG logBuilder = Sensor.SENSOR_LOG.parseFrom(tcpFlowBytes);
				String tcpFlow = JsonFormat.printToString(logBuilder.getSkyeyeTcpflow());
				log.error("tcpFlow------------------:" + tcpFlow);
				JsonParser parser = new JsonParser();
				jsonElement = parser.parse(tcpFlow);
				String sipStr ="";
				String dipStr = "";
				if(jsonElement.getAsJsonObject().get("sip")!=null) {
					sipStr = jsonElement.getAsJsonObject().get("sip").getAsString();
				}
				if(jsonElement.getAsJsonObject().get("dip")!=null) {
					dipStr = jsonElement.getAsJsonObject().get("dip").getAsString();
				}
				log.error("sipStr------------:" + sipStr);
				log.error("dipStr------------:" + dipStr);
				Result sReulst = null;
				Result dResult = null;
				if (sipStr != null) {
					sReulst = Geoip.getInstance().query(sipStr);
				}
				if (dipStr != null) {
					dResult = Geoip.getInstance().query(dipStr);
				}
				log.error("sReulst------------:" + sReulst);
				log.error("dResult------------:" + dResult);
				log.error("geo123456789------------:" + jsonElement);
				//发送到kafka
				// sendKafkaMessage(tuple, jsonElement, sReulst, dResult);
			} else {
				this.outputCollector.emit(new Values(""));
			}
			this.outputCollector.ack(tuple);
		} catch (Exception e) {
			e.printStackTrace();
			log.error(e);
			this.outputCollector.emit(new Values(tcpFlowBytes));
		}
	}

	public void sendKafkaMessage(Tuple tuple, JsonElement jsonElement, Result sReulst, Result dResult) {
		String topicProperties = producer.getTopicProperties(pro.getProperty("TOPIC_NAME_OUTPUT"));
		log.error("schema------"+topicProperties);
		Schema topicSchema = new Schema.Parser().parse(topicProperties);

		DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<GenericRecord>(topicSchema);
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
			GenericRecord record = new GenericData.Record(topicSchema);
			JsonObject jsonObject =jsonElement.getAsJsonObject();
			Set<Entry<String, JsonElement>> entry = jsonObject.entrySet();
			for (Entry<String, JsonElement> entry2 : entry) {
				if(entry2.getValue()!=null) {
					try {
						record.put(entry2.getKey(), entry2.getValue().getAsString());
					} catch (Exception e) {
						record.put(entry2.getKey(), "");
					}
				}else {
					record.put(entry2.getKey(), "");
				}
			}
			Map<String, String> sipMap = null;
			Map<String, String> dipMap = null;
			if (sReulst != null) {
				sipMap = ConvertIpToMap(sReulst);
			}
			if (dResult != null) {
				dipMap = ConvertIpToMap(dResult);
			}
			if (dipMap != null) {
				JSONObject object2Child = new JSONObject();
				for (Entry<String, String> entry3 : dipMap.entrySet()) {
					object2Child.put(entry3.getKey(), entry3.getValue());
				}
				record.put("geo_dip", object2Child);
			}else {
				record.put("geo_dip",new HashMap<String,String>());
			}
			if (sipMap != null) {
				JSONObject object2Child = new JSONObject();
				for (Entry<String, String> entry4 : sipMap.entrySet()) {
					object2Child.put(entry4.getKey(), entry4.getValue());
				}
				record.put("geo_sip", object2Child);
			}else {
				record.put("geo_sip",new HashMap<String,String>());
			}
			log.error("record----------"+record);
			datumWriter.write(record, encoder);
			encoder.flush();
			byte[] sendData = out.toByteArray();
			Future<RecordMetadata> future = producer
					.send(new ProducerRecord<String, byte[]>("ty_dns_enrichment", null, sendData));
			future.get(4, TimeUnit.SECONDS);
			
			log.error("success------"+future.isDone()+sendData.toString());
			this.outputCollector.ack(tuple);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private Map<String, String> ConvertIpToMap(Result sReulst) {
		Map<String, String> ipMap = new HashMap<String, String>();
		if (sReulst.block != null && sReulst.block.latitude != null) {
			ipMap.put("latitude", sReulst.block.latitude);
		}
		if (sReulst.block != null && sReulst.block.longitude != null) {
			ipMap.put("longitude", sReulst.block.longitude);
		}
		if (sReulst.location != null) {
			if (sReulst.location.continent_code != null) {
				ipMap.put("continent_code", sReulst.location.continent_code);
			}
			if (sReulst.location.country_code2 != null) {
				ipMap.put("country_code2", sReulst.location.country_code2);
			}
			if (sReulst.location.country_name != null) {
				ipMap.put("country_name", sReulst.location.country_name);
			}
			if (sReulst.location.subdivision != null) {
				ipMap.put("subdivision", sReulst.location.subdivision);
			}
			if (sReulst.location.city_name != null) {
				ipMap.put("city_name", sReulst.location.city_name);
			}
			if (sReulst.location.timezone != null) {
				ipMap.put("timezone", sReulst.location.timezone);
			}
		}
		return ipMap;
	}

	public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
	}
	/**
	 * blot关闭的时候调用
	 */
	public void cleanup() {
			super.cleanup();
	}
}
