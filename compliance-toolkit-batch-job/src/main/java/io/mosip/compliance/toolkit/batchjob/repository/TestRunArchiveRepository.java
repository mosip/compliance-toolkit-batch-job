package io.mosip.compliance.toolkit.batchjob.repository;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import io.mosip.compliance.toolkit.batchjob.entity.TestRunArchiveEntity;
import io.mosip.kernel.core.dataaccess.spi.repository.BaseRepository;

@Repository("TestRunArchiveRepository")
public interface TestRunArchiveRepository extends BaseRepository<TestRunArchiveEntity, String> {

	@Query(value = "select tr.id FROM toolkit.test_run_archive tr where tr.collection_id = ?1", nativeQuery = true)
	public List<String> getTestRunsForCollectionId(String collectionId);
	
	@Modifying
	@Transactional
	@Query(value = "INSERT INTO toolkit.test_run (SELECT * FROM toolkit.test_run_archive tr WHERE tr.id = ?1)", nativeQuery = true)
	public void revertCopyTestRunToArchive(String runId);
	
	@Modifying
	@Transactional
	@Query("DELETE FROM TestRunArchiveEntity e WHERE e.id = ?1")
	public void deleteById(String runId);
}
