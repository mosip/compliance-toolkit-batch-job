package io.mosip.compliance.toolkit.batchjob.tasklets;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.mosip.compliance.toolkit.batchjob.config.LoggerConfiguration;
import io.mosip.compliance.toolkit.batchjob.impl.TestRunArchivalService;
import io.mosip.kernel.core.logger.spi.Logger;

/**
 * Batch processing to move X test runs to archival tables
 *  
 * @author Mayura Deshmukh
 */
@Component
public class TestRunArchivalTasklet implements Tasklet {

	@Autowired
	private TestRunArchivalService testRunArchival;
	
	private Logger log = LoggerConfiguration.logConfig(TestRunArchivalTasklet.class);

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext arg1) throws Exception {

		try {
			testRunArchival.performArchival();
		} catch (Exception e) {
			log.error("Test Run Archival", " Tasklet ", " encountered exception ", e.getMessage());
			contribution.setExitStatus(new ExitStatus(e.getMessage()));
		}
		return RepeatStatus.FINISHED;
	}

}
