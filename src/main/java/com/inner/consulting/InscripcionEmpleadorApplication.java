package com.inner.consulting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.inner.consulting.config.DataStaxAstraProperties;;

@EnableConfigurationProperties(DataStaxAstraProperties.class)
@SpringBootApplication
public class InscripcionEmpleadorApplication {

	public static void main(String[] args) {
		SpringApplication.run(InscripcionEmpleadorApplication.class, args);
	}

	@Bean
	public CqlSessionBuilderCustomizer sessionBuilderCustomizer(DataStaxAstraProperties astraProperties) {
		Path bundle = astraProperties.getSecureConnectBundle().toPath();
		return builder -> builder.withCloudSecureConnectBundle(bundle);
	}

}
