package org.interledger.connector.server.spring.controllers.admin;

import org.interledger.connector.accounts.AccountManager;
import org.interledger.connector.server.spring.controllers.model.problems.AccountNotFoundProblem;
import org.interledger.connector.accounts.AccountId;
import org.interledger.connector.accounts.AccountSettings;
import org.interledger.connector.persistence.entities.AccountBalanceSettingsEntity;
import org.interledger.connector.persistence.entities.AccountRateLimitSettingsEntity;
import org.interledger.connector.server.spring.controllers.PathConstants;
import org.springframework.core.convert.ConversionService;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.zalando.problem.spring.common.MediaTypes;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Allows an admin to operate on Accounts in this Connector.
 */
@RestController(PathConstants.SLASH_ACCOUNTS)
public class AccountsController {

  private final AccountManager accountManager;
  private final ConversionService conversionService;

  public AccountsController(final AccountManager accountManager, final ConversionService conversionService) {
    this.accountManager = Objects.requireNonNull(accountManager);
    this.conversionService = Objects.requireNonNull(conversionService);
  }

  /**
   * Create a new Account in this server.
   *
   * @param accountSettings
   *
   * @return
   */
  @RequestMapping(
    path = PathConstants.SLASH_ACCOUNTS, method = RequestMethod.POST,
    consumes = {APPLICATION_JSON_VALUE},
    produces = {APPLICATION_JSON_VALUE, MediaTypes.PROBLEM_VALUE}
  )
  public HttpEntity<Resource<AccountSettings>> createAccount(
    @RequestBody final AccountSettings.AbstractAccountSettings accountSettings
  ) {
    Objects.requireNonNull(accountSettings);

    final AccountSettings returnableAccountSettings = this.accountManager.createAccount(accountSettings);
    final Link selfLink =
      linkTo(methodOn(AccountsController.class).getAccount(returnableAccountSettings.accountId())).withSelfRel();
    final Resource resource = new Resource(returnableAccountSettings, selfLink);

    final HttpHeaders headers = new HttpHeaders();
    final Link selfRel = linkTo(AccountsController.class).slash(accountSettings.accountId().value()).withSelfRel();
    headers.setLocation(URI.create(selfRel.getHref()));

    return new ResponseEntity(resource, headers, HttpStatus.CREATED);
  }

  /**
   * Create a new Account in this server.
   *
   * @return
   */
  @RequestMapping(method = RequestMethod.GET, produces = {APPLICATION_JSON_VALUE, MediaTypes.PROBLEM_VALUE})
  public HttpEntity<Resources<AccountSettings>> getAccounts() {

    // TODO: Add paging.
    final List<Resource<AccountSettings>> resources =
      StreamSupport.stream(this.accountManager.getAccountSettingsRepository().findAll().spliterator(), false)
        .map(accountSettingsEntity -> conversionService.convert(accountSettingsEntity, AccountSettings.class))
        .map(this::toResource)
        .collect(Collectors.toList());

    final PagedResources pagedResources = PagedResources.wrap(
      resources,
      // TODO: Connect these numbers to spring-data paging result.
      new PagedResources.PageMetadata(resources.size(), 0, resources.size())
    );

    final HttpEntity httpEntity = new HttpEntity(pagedResources);
    return httpEntity;
  }

  /**
   * Create a new Account in this server.
   *
   * @return
   */
  @RequestMapping(
    path = PathConstants.SLASH_ACCOUNTS + PathConstants.SLASH_ACCOUNT_ID, method = RequestMethod.GET,
    produces = {APPLICATION_JSON_VALUE, MediaTypes.PROBLEM_VALUE}
  )
  public Resource<AccountSettings> getAccount(
    @PathVariable(PathConstants.ACCOUNT_ID) final AccountId accountId
  ) {
    return accountManager.getAccountSettingsRepository().findByAccountId(accountId)
      .map(accountSettingsEntity -> conversionService.convert(accountSettingsEntity, AccountSettings.class))
      .map(this::toResource)
      .orElseThrow(() -> new AccountNotFoundProblem(accountId));
  }

  /**
   * Create a new Account in this server.
   *
   * @return
   */
  @RequestMapping(
    path = PathConstants.SLASH_ACCOUNTS + PathConstants.SLASH_ACCOUNT_ID, method = RequestMethod.PUT,
    produces = {APPLICATION_JSON_VALUE, MediaTypes.PROBLEM_VALUE}
  )
  public Resource<AccountSettings> updateAccount(
    @PathVariable(PathConstants.ACCOUNT_ID) final AccountId accountId,
    @RequestBody final AccountSettings.AbstractAccountSettings accountSettings
  ) {

    return accountManager.getAccountSettingsRepository().findByAccountId(accountId)
      .map(entity -> {

        // Ignore update accountId

        entity.setAssetCode(accountSettings.assetCode());
        entity.setAssetScale(accountSettings.assetScale());
        entity.setAccountRelationship(accountSettings.accountRelationship());
        entity.setBalanceSettings(
          new AccountBalanceSettingsEntity(accountSettings.balanceSettings())
        );
        entity.setConnectionInitiator(accountSettings.isConnectionInitiator());
        entity.setDescription(accountSettings.description());
        entity.setCustomSettings(accountSettings.customSettings());
        entity.setIlpAddressSegment(accountSettings.ilpAddressSegment());
        entity.setInternal(accountSettings.isInternal());
        entity.setLinkType(accountSettings.linkType());
        entity.setMaximumPacketAmount(accountSettings.maximumPacketAmount());
        entity.setRateLimitSettings(
          new AccountRateLimitSettingsEntity(accountSettings.rateLimitSettings())
        );
        entity.setReceiveRoutes(accountSettings.isReceiveRoutes());
        entity.setSendRoutes(accountSettings.isSendRoutes());

        return accountManager.getAccountSettingsRepository().save(entity);
      })
      .map(accountSettingsEntity -> conversionService.convert(accountSettingsEntity, AccountSettings.class))
      .map(this::toResource)
      .orElseThrow(() -> new AccountNotFoundProblem(accountId));
  }

  private Resource<AccountSettings> toResource(final AccountSettings accountSettings) {
    Objects.requireNonNull(accountSettings);

    final Link selfLink =
      linkTo(methodOn(AccountsController.class).getAccount(accountSettings.accountId())).withSelfRel();
    return new Resource(accountSettings, selfLink);

  }
}
