package com.xavier.sidechat.config;

import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.http.HttpHeaders;

@Configuration
@EnableElasticsearchRepositories
public class ElasticsearchConfig {
    @Value("${spring.elasticsearch.uris}")
    private String host;
    @Value("${spring.elasticsearch.username}")
    private String username;
    @Value("${spring.elasticsearch.password}")
    private String password;

    @Bean
    public RestHighLevelClient client() {
        HttpHeaders compatibilityHeaders = new HttpHeaders();
        compatibilityHeaders.add("Accept", "application/vnd.elasticsearch+json;compatible-with=7");
        compatibilityHeaders.add("Content-Type", "application/vnd.elasticsearch+json;"
                + "compatible-with=7");

        ClientConfiguration clientConfiguration
                = ClientConfiguration.builder()
                .connectedTo(host)
                .withBasicAuth(username, password) // put your credentials
                .withDefaultHeaders(compatibilityHeaders)
                .build();
        return RestClients.create(clientConfiguration).rest();
    }

    @Bean
    public ElasticsearchOperations elasticsearchTemplate() {
        return new ElasticsearchRestTemplate(client());
    }
}
