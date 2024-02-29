package io.mosip.compliance.toolkit.batchjob.repository;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import io.mosip.compliance.toolkit.batchjob.entity.TestRunDetailsEntity;
import io.mosip.compliance.toolkit.batchjob.entity.TestRunDetailsPK;
import io.mosip.kernel.core.dataaccess.spi.repository.BaseRepository;

/**
 * Batch processing to move X test runs to archival tables
 * 
 * @author Mayura Deshmukh
 *
 */
@Repository("TestRunDetailsRepository")
public interface TestRunDetailsRepository extends BaseRepository<TestRunDetailsEntity, TestRunDetailsPK> {

	@Modifying
	@Transactional
	@Query(value = "INSERT INTO toolkit.test_run_details_archive (SELECT * FROM toolkit.test_run_details trd WHERE trd.run_id = ?1)", nativeQuery = true)
	public void copyTestRunDetailsToArchive(String runId);


	@Modifying
	@Transactional
	@Query("DELETE FROM TestRunDetailsEntity e WHERE e.runId = ?1")
	public void deleteById(String runId);
	
}
