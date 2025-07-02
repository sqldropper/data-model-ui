package com.sqldropper.datamodelui;

import lombok.Cleanup;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;
import static com.sqldropper.datamodelui.TestcontainersConfiguration.NETWORK;
import java.net.URI;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Configuration
class DataModelUiApplicationTests {

	@Autowired
	PostgreSQLContainer postgreSQLContainer;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private GenericContainer schemaSpyContainer;

	private static String path = "src/main/resources/";

	@BeforeEach
	void testDatabaseConnectionAndCleanUp() throws SQLException,Exception {
		assertThat(postgreSQLContainer.isRunning()).isTrue();
		File targetFolder = new File(path + "static");
		deleteDirectoryOrFile(targetFolder);
	}

	@Test
	@SneakyThrows
	void generateDatabaseDocsForUserManagementSchema() {
		generateDocs();
	}

	private void generateDocs() throws IOException, InterruptedException, URISyntaxException {

		@Cleanup final var schemaSpy =
				new GenericContainer<>(DockerImageName.parse("schemaspy/schemaspy:6.2.4"))
						.withNetworkAliases("schemaspy")
						.withNetwork(NETWORK)
						.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("SchemaSpy")))
						.withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint(""))
						.withCommand("sleep 500000");

		schemaSpy.start();
		schemaSpy.execInContainer(
				"java",
				"-jar", "/usr/local/lib/schemaspy/schemaspy-6.2.4-app.jar",
				"-t", "pgsql11",
				"-db", postgreSQLContainer.getDatabaseName(),
				"-host", "postgres",
				"-u", postgreSQLContainer.getUsername(),
				"-p", postgreSQLContainer.getPassword(),
				"-o", "/output",
				"-dp", "/drivers_inc",
				"-all", "",
				"-debug"
		);
		schemaSpy.execInContainer("tar", "-czvf", "/output/output.tar.gz", "/output");
		URI uri = new File(path).toURI();
		final var buildFolderPath =
				Path.of(uri).toAbsolutePath();
		schemaSpy.copyFileFromContainer(
				"/output/output.tar.gz",
				buildFolderPath.resolve("output.tar.gz").toString()
		);
		schemaSpy.stop();
		final var archiver = ArchiverFactory.createArchiver("tar", "gz");
		archiver.extract(buildFolderPath.resolve("output.tar.gz").toFile(),
				buildFolderPath.toFile());
		moveToProperDirectory();

	}

	private void moveToProperDirectory() throws IOException {
		Path sourceUri = new File(path+"/output").toPath();
		Path targetUri = new File("static-pages").toPath();
		Path outputZipFile = new File(path+"output.tar.gz").toPath();
		Files.deleteIfExists(outputZipFile);
		Files.move(sourceUri, targetUri, REPLACE_EXISTING);
	}
	private static void deleteDirectoryOrFile(File targetFolder) {
		if (targetFolder.listFiles() != null) {
			Arrays.stream(targetFolder.listFiles()).toList().forEach(f -> {
				if (f.isDirectory()) {
					deleteDirectoryOrFile(f);
				}
				// delete files and empty subfolders
				f.delete();
			});
		}
	}

}
