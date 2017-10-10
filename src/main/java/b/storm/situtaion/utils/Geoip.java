package b.storm.situtaion.utils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Properties;

import org.apache.log4j.Logger;

import b.storm.situtaion.monitor.IpEnrichmentTopology;

public class Geoip implements Serializable
{
	private static Geoip geoip=new Geoip();
	private static Properties pro = new Properties();
	public static Geoip getInstance() {
		try {
			InputStream input = IpEnrichmentTopology.class.getResourceAsStream("/app.properties");
			pro.load(input);
			if(geoip==null) {
				geoip=new Geoip();
			}
			if(geoip.data.size()<=0) {
				System.out.println("load start");
				long begin = System.currentTimeMillis();
				geoip.loadData(pro.getProperty("file_path"), "GeoLite2-City-Locations-en.csv",
						"GeoLite2-City-Blocks-IPv4.csv");
				long end = System.currentTimeMillis();
				System.out.println("ip data count:-------"+geoip.data.size());
				System.out.println("load done, use time: " + (end - begin) + "ms");
			}
			return geoip;
		} catch (Exception e) {
			throw new RuntimeException("初始化ip库失败");
		}
	}
    static Logger logger = Logger.getLogger("skybuilder");
    private static final int NUMBER = 2;  
    private static final char BASE = 0;
    private Vertex root = new Vertex();
    public  HashMap<Integer,Location> data = new HashMap<Integer, Location>(); 

    public class Location {
        public String continent_code;
        public String country_code2;
        public String country_name;
        public String subdivision;
        public String city_name;
        public String timezone;
    }

    public class Block {
        public String latitude;
        public String longitude;
    }

    public class Result
    {
        public Location location;
        public Block block;
    }

    protected class Vertex
    {
        protected boolean leaf;
        protected Result result;
        protected Vertex[] child;
        Vertex()
        {
            this.leaf = false;
            this.result = result;
            child = new Vertex[NUMBER];
            for (int i = 0; i < child.length; i++) {
                child[i] = null;
            }
        }
    }

    public void insert(String word, Result res) throws Exception
    {
        char[] w = word.toCharArray();
        Vertex node = root;
        for (int i = 0; i < w.length; i++) {
            char c = w[i];
            int id = c - BASE;
            if (node.child[id] == null) {
                node.child[id] = new Vertex();
            }
            node = node.child[id];
        }
        node.leaf = true;
        node.result= res;
    }

    public Result match(String word) throws Exception
    {
        if (word == null) {
            logger.warn("invalid parameter workd: should not be null");
            return null;
        }
        char[] w = word.toCharArray();
        Vertex node = root;
        Result res = null;
        for (int i = 0; i < w.length; i++) {
            char c = w[i];
            int id = c - BASE;
            if (node.child[id] == null) {
                break;
            }
            if (node.leaf) {
                res = node.result;
            }
            node = node.child[id];
        }
        if (node.leaf) {
            res = node.result;
        }
        return res;
    }

    private void setBinary(char[] word, int id, int v)
    {
        word[id * 8 + 0] = (char)(((v & 0x80) >> 7) + BASE);
        word[id * 8 + 1] = (char)(((v & 0x40) >> 6) + BASE);
        word[id * 8 + 2] = (char)(((v & 0x20) >> 5) + BASE);
        word[id * 8 + 3] = (char)(((v & 0x10) >> 4) + BASE);
        word[id * 8 + 4] = (char)(((v & 0x08) >> 3) + BASE);
        word[id * 8 + 5] = (char)(((v & 0x04) >> 2) + BASE);
        word[id * 8 + 6] = (char)(((v & 0x02) >> 1) + BASE);
        word[id * 8 + 7] = (char)((v & 0x01) + BASE);
    }

    private String getBinary(String word) throws Exception
    {
        char[] w = word.toCharArray();
        char[] s = new char[32];
        boolean network = false;
        int v = 0;
        int id = 0;
        for (int i = 0; i < w.length; i++) {
            char c = w[i];
            switch (c) {
                case '/':
                    network = true;
                case '.':
                    setBinary(s, id++, v);
                    v = 0;
                    break;
                default:
                    v = (v * 10) + (c - '0');
            }
        }
        if (!network) {
            setBinary(s, id++, v);
        }
        if (id != 4) {
            return null;
        }
        //如果是普通ip地址，v就是最后一个字段的整数值
        //如果是网络地址，如：223.255.232.0/24，v就是24
        return String.valueOf(s, 0, network ? v : 32);
    }

    public void loadData(String path, String location_filename, String block_filename) throws Exception
    {
        try {
            String filename = path + "/" + location_filename;
            loadLocations(filename);

            filename = path + "/" + block_filename;
            loadBlocks(filename);
        } catch (Exception e) {
            logger.error("load csv exception: " + e.toString());
            throw new Exception("load csv file");
        }
    }

    public void loadLocations(String filename) throws Exception
    {
        File f = new File(filename);
        BufferedReader reader = new BufferedReader(new FileReader(f));
        String line = null;
        while ((line = reader.readLine()) != null) {
            char[] w = line.toCharArray();
            if (w[0] < 48 || w[0] > 57) {
                continue;
            }
            int step = 0;
            int pos = 0;
            int geoname_id = 0;
            Location location = new Location();
            for (int i = 0; i < w.length; i++) {
                if (w[i] != ',') {
                    continue;
                }
                step++;
                int len = i - pos;
                if (len > 1) {
                    String s = String.valueOf(w, pos, len);
                    if (s.charAt(0) == '"') {
                        s = s.substring(1, len - 1);
                    }
                    switch (step) {
                        case 1: geoname_id = Integer.parseInt(s); break;
                        case 2: break;
                        case 3: location.continent_code = s; break;
                        case 4: break;
                        case 5: location.country_code2 = s; break;
                        case 6: location.country_name = s; break;
                        case 7: break;
                        case 8: location.subdivision = s; break;
                        case 9: break;
                        case 10: break;
                        case 11: location.city_name = s; break;
                        case 12: break;
                        default: break;
                    }
                }
                pos = i + 1;
            }
            int len = w.length - pos;
            if (step == 12 && len > 1) {
                location.timezone = String.valueOf(w, pos, len);
            }
            data.put(geoname_id, location);
        }
        reader.close();
    }

    public void loadBlocks(String filename) throws Exception
    {
        if (data.isEmpty()) {
            throw new Exception("invaild locations info"); 
        }

        File f = new File(filename);
        BufferedReader reader = new BufferedReader(new FileReader(f));
        String line = null;
        while ((line = reader.readLine()) != null) {
            char[] w = line.toCharArray();
            if (w[0] < 48 || w[0] > 57) {
                continue;
            }
            int step = 0;
            int pos = 0;
            int geoname_id = 0;
            String network = null;
            Block block = new Block();
            String ip = null;
            for (int i = 0; i < w.length; i++) {
                if (w[i] != ',') {
                    continue;
                }
                step++;
                int len = i - pos;
                if (len > 1) {
                    String s = String.valueOf(w, pos, len);
                    if (s.charAt(0) == '"') {
                        s = s.substring(1, len - 1);
                    }
                    switch (step) {
                        case 1: 
                            network = getBinary(s); 
                            ip = s;
                            break;
                        case 2: 
                            geoname_id = Integer.parseInt(s); 
                            break;
                       case 3: break;
                        case 4: break;
                        case 5: break;
                        case 6: break;
                        case 7: break;
                        case 8: 
                            block.latitude = s; 
                            break;
                        default: 
                            break;
                    }
                }
                pos = i + 1;
            }
            int len = w.length - pos;
            if (step == 8 && len > 1) {
                block.longitude = String.valueOf(w, pos, len);
            }

            if (network == null) {
                logger.debug("network: " + ip + ", invalid network binary, should not be null");
                continue;
            }
            Location location = data.get(geoname_id);
            if (location == null) {
                logger.debug("network: " + ip + ", geoname_id: " + geoname_id + " have no correspondingly locations");
                continue;
            }
            Result res = new Result();
            res.location = location;
            res.block = block;
            insert(network, res);
        }
        reader.close();
    }


    public Result query(String word) throws Exception
    {
        return match(getBinary(word));
    }

    public static void PrintResult(Result res) {
        if (res == null) {
            System.out.println("res is null");
            return;
        }
        System.out.println("result: latitude=" + res.block.latitude);
        System.out.println("result: longitude=" + res.block.longitude);
        System.out.println("result: continent_code=" + res.location.continent_code);
        System.out.println("result: country_code2=" + res.location.country_code2);
        System.out.println("result: country_name=" + res.location.country_name);
        System.out.println("result: subdivision=" + res.location.subdivision);
        System.out.println("result: city_name=" + res.location.city_name);
        System.out.println("result: timezone=" + res.location.timezone);
        System.out.println("");
    }

    public static void main(String args[])
    {
        try {
            Geoip geo = new Geoip();
            System.out.println("load start");
            long begin = System.currentTimeMillis();
            geo.loadData("E:\\test", "GeoLite2-City-Locations-en.csv", "GeoLite2-City-Blocks-IPv4.csv");
            long end = System.currentTimeMillis();
            System.out.println("load done, use time: " + (end - begin) + "ms");

            long total = 0;
            long middle = 0;
            for (int i = 0; i < 100; i++) {
                begin = System.nanoTime();
                Result res = geo.query("114.115.191.24");
  //            res = geo.query("106.38.199.36");
                end = System.nanoTime();
                middle += (end - begin);
                if (res != null) {
                    PrintResult(res);
                }
                //if (i % 10000 == 0) {
                //    total += middle;
                //    System.out.println("index: " + i + ", average time: " + (middle / 10000) + "ns");
                //    middle = 0;
                //    //if (res != null) {
                //    //    PrintResult(res);
                //    //}
                //}
            }

            if (middle != 0) {
                total += middle;
                middle = 0;
            }

            System.out.println("average time: " + (total / 1000000) + "ns");

            Thread.sleep(200000);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
