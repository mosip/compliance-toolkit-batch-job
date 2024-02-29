package io.mosip.compliance.toolkit.batchjob.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import io.mosip.compliance.toolkit.batchjob.entity.ComplianceTestRunSummaryEntity;
import io.mosip.compliance.toolkit.batchjob.entity.ComplianceTestRunSummaryPK;
import io.mosip.kernel.core.dataaccess.spi.repository.BaseRepository;

@Repository("ComplianceTestRunSummaryRepository")
public interface ComplianceTestRunSummaryRepository
        extends BaseRepository<ComplianceTestRunSummaryEntity, ComplianceTestRunSummaryPK> {

	 @Query("SELECT t FROM ComplianceTestRunSummaryEntity t WHERE t.collectionId = :collectionId")
	public Optional<ComplianceTestRunSummaryEntity> getComplianceTestRunSummaryForCollectionId(String collectionId);
}
