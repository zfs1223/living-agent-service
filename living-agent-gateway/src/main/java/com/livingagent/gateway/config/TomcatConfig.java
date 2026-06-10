package com.livingagent.gateway.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    @Bean
    public ConfigurableServletWebServerFactory webServerFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.addConnectorCustomizers(connector -> {
            connector.setProperty("allowEncodedSlashes", "true");
            connector.setProperty("decodeURIComponent", "true");
            connector.setProperty("relaxedQueryChars", "|{}[]\\");
            connector.setProperty("relaxedPathChars", "|{}[]\\");
        });
        return factory;
    }
}
