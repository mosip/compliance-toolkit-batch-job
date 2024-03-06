package io.mosip.compliance.toolkit.batchjob;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = { "io.mosip.compliance.toolkit.batchjob.*", "${mosip.auth.adapter.impl.basepackage}"})
public class ToolkitBatchJobApplication {

	public static void main(String[] args) {
		SpringApplication.run(ToolkitBatchJobApplication.class, args);
	}

}
