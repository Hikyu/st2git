package org.sync.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import org.apache.commons.io.IOUtils;


public class Config {
    private final String configFile = "config.properties";
    private Properties properties;
    public static Config instance = new Config();
    
    private Config() {
        properties = new Properties();
        try {
            properties.load(new ByteArrayInputStream(new ClassPathResource(configFile).getBytes()));
        } catch (IOException e) {
            System.err.println("读取配置文件失败！");
            e.printStackTrace();
        }
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
