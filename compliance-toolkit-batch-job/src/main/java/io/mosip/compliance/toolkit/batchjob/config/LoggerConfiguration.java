package io.mosip.compliance.toolkit.batchjob.config;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.logger.logback.factory.Logfactory;

/**
 * Batch processing to move X test runs to archival tables
 * 
 * @author Mayura Deshmukh
 *
 */
public class LoggerConfiguration {

	/**
	 * Instantiates a new logger.
	 */
	private LoggerConfiguration() {

	}

	public static Logger logConfig(Class<?> clazz) {
		return Logfactory.getSlf4jLogger(clazz);
	}

}
