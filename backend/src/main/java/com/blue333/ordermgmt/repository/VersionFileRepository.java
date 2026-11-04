package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.VersionFile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Restricted repository: version files are retained permanently (NFR-F4), so no delete methods are exposed. */
public interface VersionFileRepository extends Repository<VersionFile, Long> {
  VersionFile save(VersionFile file);

  Optional<VersionFile> findByVersionId(Long versionId);

  List<VersionFile> findAllByVersionIdIn(Collection<Long> versionIds);

  boolean existsByStoragePath(String storagePath);

  /** P4-T11: referenced object keys, used by the orphan sweep. */
  @Query("select f.storagePath from VersionFile f")
  List<String> findAllStoragePaths();
}
