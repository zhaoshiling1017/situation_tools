package b.storm.situtaion.monitor;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.AlreadyAliveException;
import backtype.storm.generated.AuthorizationException;
import backtype.storm.generated.InvalidTopologyException;
import backtype.storm.spout.SchemeAsMultiScheme;
import backtype.storm.topology.TopologyBuilder;
import storm.kafka.BrokerHosts;
import storm.kafka.KafkaSpout;
import storm.kafka.SpoutConfig;
import storm.kafka.ZkHosts;

public class IpEnrichmentTopology {

	private static final Logger log = Logger.getLogger(IpEnrichmentTopology.class);

	// 各个组件名字的唯一标识
	private final static String GEO_IP_SPOUT_ID = "geo_ip-spout";
	private final static String IP_ENRICHMENT_BOLT_ID = "ipenrichment-bolt";
	
	private static Properties pro = new Properties();
	
	static {
		// 初始化
		initData();
	}
	public static void main(String[] args)
			throws AlreadyAliveException, InvalidTopologyException, AuthorizationException {
		String zkRoot = "";
		Config conf = new Config();
		Map<String, String> map = new HashMap<String, String>();
		map.put("metadata.broker.list", pro.getProperty("broker_url"));

		// serializer.class为消息的序列化类
		map.put("serializer.class", "kafka.serializer.StringEncoder");

		// 配置zookeeper 主机:端口号
		BrokerHosts brokerHosts = new ZkHosts(pro.getProperty("zk_hots"));

		SpoutConfig spoutConfig = new SpoutConfig(brokerHosts, pro.getProperty("TOPIC_NAME_INPUT"), zkRoot,
				GEO_IP_SPOUT_ID);

		// 设置如何处理kafka消息队列输入流
		spoutConfig.scheme = new SchemeAsMultiScheme(new MessageScheme());

		// 建立拓扑DAG
		IpEnrichmentSolt ipEnrichmentSolt = new IpEnrichmentSolt();
		// 构建一个拓扑Builder
		TopologyBuilder topologyBuilder = new TopologyBuilder();

		// 配置第一个组件GEO_IP_SPOUT_ID
		topologyBuilder.setSpout(GEO_IP_SPOUT_ID, new KafkaSpout(spoutConfig), Integer.parseInt(pro.getProperty("SpoutMax","5")));
		
		// IP_ENRICHMENT_BOLT_ID
		topologyBuilder.setBolt(IP_ENRICHMENT_BOLT_ID, ipEnrichmentSolt,1).shuffleGrouping(GEO_IP_SPOUT_ID);

		// 输出调试信息
		conf.setDebug(true);
		
		// 远程集群
		if (args != null && args.length > 0) {
			conf.setNumWorkers(Integer.parseInt(pro.getProperty("nimbus_worker_num")));
			conf.setMaxSpoutPending(Integer.parseInt(pro.getProperty("MaxSpoutPending")));
			StormSubmitter.submitTopologyWithProgressBar(pro.getProperty("topology_name"), conf, topologyBuilder.createTopology());
		} else {
			// 设置一个spout task中处于pending状态的最大的tuples数量
			conf.put(Config.TOPOLOGY_MAX_SPOUT_PENDING, 1);
			// 设置任务的并行度
			conf.put(Config.TOPOLOGY_MAX_TASK_PARALLELISM, 1);
			// 建立本地集群
			LocalCluster cluster = new LocalCluster();
			// 创建拓扑实例,并提交到本地集群进行运行
			cluster.submitTopology(pro.getProperty("topology_name"), conf, topologyBuilder.createTopology());
		}
	}
	/**
	 * 加载配置文件
	 */
	private static void initData() {
		try {
			InputStream input = IpEnrichmentTopology.class.getResourceAsStream("/app.properties");
			pro.load(input);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("找不到app.properties,文件不存在");
		}
	}
}
