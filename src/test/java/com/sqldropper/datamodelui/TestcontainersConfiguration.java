package com.sqldropper.datamodelui;

import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.GenericContainer;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    public static final Network NETWORK = Network.newNetwork();
    @Bean
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"))
                .withNetworkAliases("postgres")
                .withUsername("root")
                .withPassword("pass")
                .withDatabaseName("SQL-DROPPER")
                .withInitScripts("init.sql","create_table.sql")
                .withNetwork(NETWORK);
    }

    @Bean
    static GenericContainer<?> schemaSpyContainer() {
        var schemaSpy = new GenericContainer<>(DockerImageName.parse("schemaspy/schemaspy:6.2.4"))
                .withNetworkAliases("schemaspy")
                .withNetwork(NETWORK)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("SchemaSpy")))
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint(""))
                .withCommand("sleep 500000");
        return schemaSpy;
    }

}
