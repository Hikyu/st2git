package org.sync.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;

import jdk.nashorn.internal.ir.annotations.Ignore;

/**
 * 
 * @author Yukai
 *
 */
public class Config {
    private final String configFile = "config.properties";
    private final String tdReqCsvFile = "td-req.csv";
    private Properties properties;
    public static Config instance = new Config();
    private Map<String, List<String>> tdReqNameSeqMap = new HashMap<>();
    private final int reqStartNumber = 30000;
    private final String reviewBoardAPI = "http://192.168.101.27/api/review-requests/";
    
    private Config() {
        properties = new Properties();
        try {
            properties.load(new ByteArrayInputStream(new ClassPathResource(configFile).getBytes()));
        } catch (IOException e) {
            System.err.println("读取配置文件失败！");
            e.printStackTrace();
        }
        
        
        try {
            // 读取csv文件，TD需求编号及其对应的序号
            BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(new ClassPathResource(tdReqCsvFile).getBytes())));
            reader.readLine();//第一行信息，为标题信息
            String line = null; 
            while((line=reader.readLine())!=null){ 
                String item[] = line.split(",");
                String reqName = item[0];
                String reqSeq = item[1];
                if (tdReqNameSeqMap.containsKey(reqName)) {
                    tdReqNameSeqMap.get(reqName).add(reqSeq);
                } else {
                    List<String> seqList = new ArrayList<>();
                    seqList.add(reqSeq);
                    tdReqNameSeqMap.put(reqName, seqList);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } 
    }
    
    public List<String> getReqSeqbyReqName(String reqName) {
        List<String> seqList = tdReqNameSeqMap.get(reqName);
        if (seqList != null) {
            return seqList;
        }
        return new ArrayList<>();
    }
    
    public int getTdReqStartNumber() {
        return reqStartNumber;
    }
    
    public String getReviewBoardRestDomain() {
        return reviewBoardAPI;
    }
    
    public String get(String key) {
       return properties.getProperty(key);
    }
    
    public String get(String key, String def) {
        return properties.getProperty(key, def);
    }
    
    class ClassPathResource {
        private ClassLoader classLoader;
        private String path;
        public ClassPathResource(String path) {
            this.path = path;
        }
        public byte[] getBytes() throws IOException {
            URL url = resolveURL();
            byte[] byteArray = IOUtils.toByteArray(url.openStream());
            return byteArray;
        }
        
        public String getString() throws IOException {
            return new String(getBytes());
        }
        private URL resolveURL() throws IOException {
            if (this.classLoader == null) {
                classLoader = getDefaultClassLoader();
            }
            URL url = classLoader.getResource(path);
            if (url != null) {
                return url;
            }
            url = ClassLoader.getSystemResource(path);
            if (url == null) {
                throw new IOException(String.format("Error opening %s", path));
            }
            return url;
        }
        private ClassLoader getDefaultClassLoader() {
            ClassLoader cl = null;
            try {
                cl = Thread.currentThread().getContextClassLoader();
            } catch (Throwable ex) {
            }
            if (cl == null) {
                cl = ClassPathResource.class.getClassLoader();
                if (cl == null) {
                    try {
                        cl = ClassLoader.getSystemClassLoader();
                    } catch (Throwable ex) {
                    }
                }
            }
            return cl;
        }
    }
}
