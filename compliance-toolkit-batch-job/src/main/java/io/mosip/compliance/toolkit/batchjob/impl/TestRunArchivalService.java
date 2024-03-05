package io.mosip.compliance.toolkit.batchjob.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.mosip.compliance.toolkit.batchjob.config.LoggerConfiguration;
import io.mosip.compliance.toolkit.batchjob.entity.ComplianceTestRunSummaryEntity;
import io.mosip.compliance.toolkit.batchjob.repository.ComplianceTestRunSummaryRepository;
import io.mosip.compliance.toolkit.batchjob.repository.TestRunArchiveRepository;
import io.mosip.compliance.toolkit.batchjob.repository.TestRunDetailsArchiveRepository;
import io.mosip.compliance.toolkit.batchjob.repository.TestRunDetailsRepository;
import io.mosip.compliance.toolkit.batchjob.repository.TestRunRepository;
import io.mosip.kernel.core.logger.spi.Logger;

/**
 * Batch processing to move X test runs to archival tables
 * 
 * @author Mayura Deshmukh
 *
 */
@Component
public class TestRunArchivalService {

	@Autowired
	TestRunRepository testRunRepository;

	@Autowired
	TestRunDetailsRepository testRunDetailsRepository;

	@Autowired
	TestRunArchiveRepository testRunArchiveRepository;

	@Autowired
	TestRunDetailsArchiveRepository testRunDetailsArchiveRepository;

	@Autowired
	ComplianceTestRunSummaryRepository complianceTestRunSummaryRepository;

	@Value("${mosip.toolkit.batchjob.enable.testrun.archival}")
	private String enableTestRunArchival;

	@Value("${mosip.toolkit.batchjob.testrun.archive.offset}")
	private int archiveOffset;

	@Value("#{'${mosip.toolkit.batchjob.archival.revert.collectionids}'.split(',')}")
	private List<String> revertCollectionIds;

	private Logger LOGGER = LoggerConfiguration.logConfig(TestRunArchivalService.class);

	public void performArchival() throws Exception {
		try {
			if ("true".equalsIgnoreCase(enableTestRunArchival)) {
				if (archiveOffset >= 0) {
					LOGGER.info("Starting Test Run Archival Job: "
							+ LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
					List<String> collectionIds = testRunRepository
							.getCollectionIdsForTestRunsToBeArchived(archiveOffset);
					LOGGER.info("Total number of collections for which test runs are to be archived: "
							+ collectionIds.size());
					for (String collectionId : collectionIds) {
						LOGGER.info("----------------------------------------------------------------------------");
						// validate if this collection id
						Optional<ComplianceTestRunSummaryEntity> optionalEntity = complianceTestRunSummaryRepository
								.getComplianceTestRunSummaryForCollectionId(collectionId);
						if (!optionalEntity.isPresent()) {
							if (!revertCollectionIds.contains(collectionId)) {
								LOGGER.info("Starting test run archival for collection_id: " + collectionId);
								List<String> testRunIds = testRunRepository.getTestRunsForCollectionId(collectionId);
								LOGGER.info("Total number of test runs available: " + testRunIds.size());
								int index = 0;
								int count = testRunIds.size() - archiveOffset;
								LOGGER.info("Total number of test runs to be archived: " + count);
								for (String testRunId : testRunIds) {
									if (index < count) {
										LOGGER.info("Archiving test run with id: " + testRunId);
										this.archiveTestRun(testRunId);
										LOGGER.info("Archival done for test run with id: " + testRunId);
									} else {
										break;
									}
									index++;
								}
							} else {
								LOGGER.info(
										"This collection archival is to be reverted, hence cannot archive the test run collection_id: "
												+ collectionId);
							}
						} else {
							LOGGER.info(
									"Reports are associated for this collection, hence cannot archive the test run collection_id: "
											+ collectionId);
						}
						LOGGER.info("----------------------------------------------------------------------------");
					}
				} else {
					LOGGER.info("Archive offset should be more than or equal to 0");
				}
				// now do the revert for any collection ids, if specified
				if (!revertCollectionIds.isEmpty()) {
					LOGGER.info("Forcefully reverting test run archival for collection ids : "
							+ revertCollectionIds.size());
					for (String collectionId : revertCollectionIds) {
						List<String> testRunIds = testRunArchiveRepository.getTestRunsForCollectionId(collectionId);
						LOGGER.info("Total number of test runs to be reverted for the collection: " + collectionId
								+ " is: " + testRunIds.size());
						for (String testRunId : testRunIds) {
							LOGGER.info("Reverting archival of test run with id: " + testRunId);
							this.revertArchiveTestRun(testRunId);
							LOGGER.info("Reverting of archival done for test run with id: " + testRunId);
						}
					}
				}
			} else {
				LOGGER.info("Test Run Archival is NOT enabled");
			}
			LOGGER.info(
					"Completed Test Run Archival Job: " + LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
		} catch (Exception ex) {
			LOGGER.error("Test Run Archival job failed due to error ", ex.getMessage(), null);
			throw ex;
		}
	}

	@Transactional
	public void archiveTestRun(String runId) {
		testRunDetailsRepository.copyTestRunDetailsToArchive(runId);
		testRunRepository.copyTestRunToArchive(runId);
		testRunDetailsRepository.deleteById(runId);
		testRunRepository.deleteById(runId);
	}

	@Transactional
	public void revertArchiveTestRun(String runId) {
		testRunArchiveRepository.revertCopyTestRunToArchive(runId);
		testRunDetailsArchiveRepository.revertCopyTestRunDetailsToArchive(runId);
		testRunDetailsArchiveRepository.deleteById(runId);
		testRunArchiveRepository.deleteById(runId);
	}
}
