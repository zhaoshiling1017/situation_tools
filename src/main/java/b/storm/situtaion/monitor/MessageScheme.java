package b.storm.situtaion.monitor;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import backtype.storm.spout.Scheme;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;

public class MessageScheme implements Scheme {

	private static final Logger LOGGER = LoggerFactory.getLogger(MessageScheme.class);

	public Fields getOutputFields() {
		return new Fields("msg");
	}
	public List<Object> deserialize(byte[] ser) {
		try {
			// 从kafka中读取的值直接序列化为UTF-8的str
			String mString = new String(ser,"utf-8");
			return new Values(mString);
		} catch (Exception e) {
			e.printStackTrace();
			LOGGER.error("Cannot parse the provided message");
		}
		return null;
	}
}