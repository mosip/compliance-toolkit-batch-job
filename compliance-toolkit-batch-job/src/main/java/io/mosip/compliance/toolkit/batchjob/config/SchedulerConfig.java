package io.mosip.compliance.toolkit.batchjob.config;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.mosip.kernel.core.logger.spi.Logger;

/**
 * Batch processing to move X test runs to archival tables
 * 
 * @author Mayura Deshmukh
 *
 */
@RefreshScope
@Component
@EnableScheduling
public class SchedulerConfig {

	@Autowired
	JobLauncher jobLauncher;

	@Autowired
	Job job;

	private static final String LOGDISPLAY = "{} - {} - {}";

	private static final String JOB_STATUS = "Job's status";

	private Logger LOGGER = LoggerConfiguration.logConfig(SchedulerConfig.class);

	@Scheduled(cron = "${mosip.toolkit.batchjob.schedule.cron.testRunArchivalJob}")
	public void testRunArchivalJobScheduler() throws Exception {
		LOGGER.info(LOGDISPLAY, JOB_STATUS, "", "Batch job starting");
		JobParameters jobParam = new JobParametersBuilder().addLong("testRunArchivalTime", System.currentTimeMillis())
				.toJobParameters();
		try {
			JobExecution jobExecution = jobLauncher.run(job, jobParam);

			LOGGER.info(LOGDISPLAY, JOB_STATUS, jobExecution.getId().toString(), jobExecution.getStatus().toString());

		} catch (JobExecutionAlreadyRunningException | JobRestartException | JobInstanceAlreadyCompleteException
				| JobParametersInvalidException e) {

			LOGGER.error(LOGDISPLAY, "Test Run Archival job failed due to error ", e.getMessage(), null);
		}

	}
}