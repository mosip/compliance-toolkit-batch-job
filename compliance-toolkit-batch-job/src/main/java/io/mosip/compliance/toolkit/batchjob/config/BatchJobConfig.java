package io.mosip.compliance.toolkit.batchjob.config;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.mosip.compliance.toolkit.batchjob.tasklets.TestRunArchivalTasklet;

/**
 * Batch processing to move X test runs to archival tables
 * 
 * @author Mayura Deshmukh
 *
 */
@Configuration
@EnableBatchProcessing
public class BatchJobConfig {
	
	@Autowired
	private JobBuilderFactory jobBuilderFactory;

	@Autowired
	private StepBuilderFactory stepBuilderFactory;

	@Autowired
	private TestRunArchivalTasklet testRunArchivalTasklet;

	@Bean
	public Step testRunArchivalStep() {
		return stepBuilderFactory.get("testRunArchivalStep").tasklet(testRunArchivalTasklet).build();
	}

	@Bean
	public Job testRunArchivalJob() {
		return this.jobBuilderFactory.get("testRunArchivalJob").incrementer(new RunIdIncrementer())
				.start(testRunArchivalStep()).build();
	}

}
