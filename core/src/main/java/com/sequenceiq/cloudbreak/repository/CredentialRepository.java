package com.sequenceiq.cloudbreak.repository;

import static com.sequenceiq.cloudbreak.workspace.resource.ResourceAction.READ;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sequenceiq.cloudbreak.workspace.repository.DisableHasPermission;
import com.sequenceiq.cloudbreak.workspace.repository.check.CheckPermissionsByWorkspaceId;
import com.sequenceiq.cloudbreak.workspace.repository.check.WorkspaceResourceType;
import com.sequenceiq.cloudbreak.workspace.resource.WorkspaceResource;
import com.sequenceiq.cloudbreak.domain.Credential;
import com.sequenceiq.cloudbreak.workspace.repository.workspace.WorkspaceResourceRepository;
import com.sequenceiq.cloudbreak.workspace.repository.EntityType;

@DisableHasPermission
@EntityType(entityClass = Credential.class)
@Transactional(TxType.REQUIRED)
@WorkspaceResourceType(resource = WorkspaceResource.CREDENTIAL)
public interface CredentialRepository extends WorkspaceResourceRepository<Credential, Long> {

    @CheckPermissionsByWorkspaceId(action = READ)
    @Query("SELECT c FROM Credential c WHERE c.workspace.id= :workspaceId AND c.archived IS FALSE AND cloudPlatform IN (:cloudPlatforms)")
    Set<Credential> findActiveForWorkspaceFilterByPlatforms(@Param("workspaceId") Long workspaceId, @Param("cloudPlatforms") Collection<String> cloudPlatforms);

    @CheckPermissionsByWorkspaceId(action = READ, workspaceIdIndex = 1)
    @Query("SELECT c FROM Credential c WHERE c.name= :name AND c.workspace.id= :workspaceId AND c.archived IS FALSE AND cloudPlatform IN (:cloudPlatforms)")
    Optional<Credential> findActiveByNameAndWorkspaceIdFilterByPlatforms(@Param("name") String name, @Param("workspaceId") Long workspaceId,
                    @Param("cloudPlatforms") Collection<String> cloudPlatforms);

    @CheckPermissionsByWorkspaceId(action = READ, workspaceIdIndex = 1)
    @Query("SELECT c FROM Credential c WHERE c.id= :id AND c.workspace.id= :workspaceId AND c.archived IS FALSE AND cloudPlatform IN (:cloudPlatforms)")
    Optional<Credential> findActiveByIdAndWorkspaceFilterByPlatforms(@Param("id") Long id, @Param("workspaceId") Long workspaceId,
                    @Param("cloudPlatforms") Collection<String> cloudPlatforms);

}