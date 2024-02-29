package io.mosip.compliance.toolkit.batchjob.repository;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import io.mosip.compliance.toolkit.batchjob.entity.TestRunDetailsArchiveEntity;
import io.mosip.compliance.toolkit.batchjob.entity.TestRunDetailsPK;
import io.mosip.kernel.core.dataaccess.spi.repository.BaseRepository;

@Repository("TestRunDetailsArchiveRepository")
public interface TestRunDetailsArchiveRepository extends BaseRepository<TestRunDetailsArchiveEntity, TestRunDetailsPK> {

	
	@Modifying
	@Transactional
	@Query(value = "INSERT INTO toolkit.test_run_details (SELECT * FROM toolkit.test_run_details_archive trd WHERE trd.run_id = ?1)", nativeQuery = true)
	public void revertCopyTestRunDetailsToArchive(String runId);
	
	@Modifying
	@Transactional
	@Query("DELETE FROM TestRunDetailsArchiveEntity e WHERE e.runId = ?1")
	public void deleteById(String runId);
	
}
