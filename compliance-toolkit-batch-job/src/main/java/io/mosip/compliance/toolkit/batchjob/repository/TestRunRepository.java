package io.mosip.compliance.toolkit.batchjob.repository;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import io.mosip.compliance.toolkit.batchjob.entity.TestRunEntity;
import io.mosip.kernel.core.dataaccess.spi.repository.BaseRepository;

/**
 * Batch processing to move X test runs to archival tables
 * 
 * @author Mayura Deshmukh
 *
 */
@Repository("TestRunRepository")
public interface TestRunRepository extends BaseRepository<TestRunEntity, String> {

	@Query(value = "select s.collection_id from (select count(tr.id) as count_of_test_runs, tr.collection_id as collection_id FROM toolkit.test_run tr where tr.is_deleted<>'true' group by tr.collection_id, tr.partner_id) s where s.count_of_test_runs > ?1", nativeQuery = true)
	public List<String> getCollectionIdsForTestRunsToBeArchived(int archivalThreshold);

	@Query(value = "select tr.id FROM toolkit.test_run tr where tr.collection_id = ?1 AND tr.is_deleted<>'true' order by tr.collection_id, tr.run_dtimes asc", nativeQuery = true)
	public List<String> getTestRunsForCollectionId(String collectionId);

	@Modifying
	@Transactional
	@Query(value = "INSERT INTO toolkit.test_run_archive (SELECT * FROM toolkit.test_run tr WHERE tr.id = ?1)", nativeQuery = true)
	public void copyTestRunToArchive(String runId);

	@Modifying
	@Transactional
	@Query("DELETE FROM TestRunEntity e WHERE e.id = ?1")
	public void deleteById(String runId);
}
