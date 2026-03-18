package com.livingagent.core.database.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class QdrantConfig {

    private static final Logger log = LoggerFactory.getLogger(QdrantConfig.class);

    @Value("${qdrant.host}")
    private String host;
    
    @Value("${qdrant.port:6333}")
    private int port;
    
    @Value("${qdrant.grpc-port:6334}")
    private int grpcPort;

    @Value("${qdrant.api-key:}")
    private String apiKey;

    @Value("${qdrant.timeout:30}")
    private int timeoutSeconds;

    @Value("${qdrant.collection-prefix:living_agent_}")
    private String collectionPrefix;

    @Bean
    public QdrantClient qdrantClient() {
        log.info("Configuring Qdrant client: {}:{}", host, grpcPort);

        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(host, grpcPort, apiKey != null && !apiKey.isEmpty())
                .withTimeout(Duration.ofSeconds(timeoutSeconds));

        if (apiKey != null && !apiKey.isEmpty()) {
            builder.withApiKey(apiKey);
        }

        return new QdrantClient(builder.build());
    }

    public String getCollectionPrefix() {
        return collectionPrefix;
    }

    public String getFullCollectionName(String collectionName) {
        return collectionPrefix + collectionName;
    }
}
