package com.sequenceiq.cloudbreak.controller;

import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.validation.Valid;

import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.api.endpoint.v1.RecipeEndpoint;
import com.sequenceiq.cloudbreak.api.endpoint.v3.RecipeV3Endpoint;
import com.sequenceiq.cloudbreak.api.model.RecipeRequest;
import com.sequenceiq.cloudbreak.api.model.RecipeResponse;
import com.sequenceiq.cloudbreak.common.model.user.IdentityUser;
import com.sequenceiq.cloudbreak.common.type.ResourceEvent;
import com.sequenceiq.cloudbreak.domain.Recipe;
import com.sequenceiq.cloudbreak.domain.security.Organization;
import com.sequenceiq.cloudbreak.service.AuthenticatedUserService;
import com.sequenceiq.cloudbreak.service.organization.OrganizationService;
import com.sequenceiq.cloudbreak.service.recipe.RecipeService;

@Component
@Transactional(TxType.NEVER)
public class RecipeController extends NotificationController implements RecipeEndpoint, RecipeV3Endpoint {

    @Inject
    @Named("conversionService")
    private ConversionService conversionService;

    @Inject
    private AuthenticatedUserService authenticatedUserService;

    @Inject
    private OrganizationService organizationService;

    @Inject
    private RecipeService recipeService;

    @Override
    public RecipeResponse get(Long id) {
        Recipe recipe = recipeService.get(id);
        return conversionService.convert(recipe, RecipeResponse.class);
    }

    @Override
    public void delete(Long id) {
        IdentityUser identityUser = authenticatedUserService.getCbUser();
        Recipe deleted = recipeService.delete(id);
        notify(identityUser, ResourceEvent.RECIPE_DELETED);
        conversionService.convert(deleted, RecipeResponse.class);
    }

    @Override
    public RecipeRequest getRequestfromName(String name) {
        Organization organization = organizationService.getDefaultOrganizationForCurrentUser();
        Recipe recipe = recipeService.getByNameForOrganization(name, organization);
        return conversionService.convert(recipe, RecipeRequest.class);
    }

    @Override
    public RecipeResponse postPublic(RecipeRequest recipeRequest) {
        return createInDefaultOrganization(recipeRequest);
    }

    @Override
    public RecipeResponse postPrivate(RecipeRequest recipeRequest) {
        return createInDefaultOrganization(recipeRequest);
    }

    @Override
    public Set<RecipeResponse> getPrivates() {
        Organization organization = organizationService.getDefaultOrganizationForCurrentUser();
        return listByOrganizationId(organization.getId());
    }

    @Override
    public Set<RecipeResponse> getPublics() {
        Organization organization = organizationService.getDefaultOrganizationForCurrentUser();
        return listByOrganizationId(organization.getId());
    }

    @Override
    public RecipeResponse getPrivate(String name) {
        return getByName(name);
    }

    @Override
    public RecipeResponse getPublic(String name) {
        return getByName(name);
    }

    @Override
    public void deletePublic(String name) {
        deleteInDefaultOrganization(name);
    }

    @Override
    public void deletePrivate(String name) {
        deleteInDefaultOrganization(name);
    }

    @Override
    public Set<RecipeResponse> listByOrganization(Long organizationId) {
        return listByOrganizationId(organizationId);
    }

    @Override
    public RecipeResponse getByNameInOrganization(Long organizationId, String name) {
        Recipe recipe = recipeService.getByNameForOrganization(name, organizationId);
        return conversionService.convert(recipe, RecipeResponse.class);
    }

    @Override
    public RecipeResponse createInOrganization(Long organizationId, @Valid RecipeRequest request) {
        Recipe recipe = conversionService.convert(request, Recipe.class);
        recipe = recipeService.create(recipe, organizationId);
        return notifyAndReturn(recipe, authenticatedUserService.getCbUser(), ResourceEvent.RECIPE_CREATED);
    }

    private void deleteInDefaultOrganization(String name) {
        Recipe deleted = recipeService.deleteByNameFromDefaultOrganization(name);
        IdentityUser identityUser = authenticatedUserService.getCbUser();
        notifyAndReturn(deleted, identityUser, ResourceEvent.RECIPE_DELETED);
    }

    @Override
    public RecipeResponse deleteInOrganization(Long organizationId, String name) {
        Recipe deleted = recipeService.deleteByNameFromOrganization(name, organizationId);
        IdentityUser identityUser = authenticatedUserService.getCbUser();
        return notifyAndReturn(deleted, identityUser, ResourceEvent.RECIPE_DELETED);
    }

    private RecipeResponse getByName(String name) {
        Organization organization = organizationService.getDefaultOrganizationForCurrentUser();
        Recipe recipe = recipeService.getByNameForOrganization(name, organization.getId());
        return conversionService.convert(recipe, RecipeResponse.class);
    }

    private Set<RecipeResponse> listByOrganizationId(Long organizationId) {
        return recipeService.listByOrganizationId(organizationId).stream()
                .map(recipe -> conversionService.convert(recipe, RecipeResponse.class))
                .collect(Collectors.toSet());
    }

    private RecipeResponse createInDefaultOrganization(@Valid RecipeRequest request) {
        Recipe recipe = conversionService.convert(request, Recipe.class);
        recipe = recipeService.createInDefaultOrganization(recipe);
        return notifyAndReturn(recipe, authenticatedUserService.getCbUser(), ResourceEvent.RECIPE_CREATED);
    }

    private RecipeResponse notifyAndReturn(Recipe recipe, IdentityUser cbUser, ResourceEvent recipeCreated) {
        notify(cbUser, recipeCreated);
        return conversionService.convert(recipe, RecipeResponse.class);
    }
}
