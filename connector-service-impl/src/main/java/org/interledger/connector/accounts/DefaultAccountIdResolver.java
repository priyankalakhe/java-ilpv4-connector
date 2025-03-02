package org.interledger.connector.accounts;

import com.google.common.annotations.VisibleForTesting;
import org.interledger.btp.BtpSession;
import org.interledger.btp.BtpSessionCredentials;
import org.interledger.connector.link.Link;
import org.springframework.security.core.Authentication;

import java.util.Objects;

/**
 * Default implementation of {@link AccountIdResolver} that looks in the connector config to find corresponding
 * AccountId definitions. If none is found, it returns a default account settings.
 */
public class DefaultAccountIdResolver implements BtpAccountIdResolver, BlastAccountIdResolver, AccountIdResolver {

  @Override
  public AccountId resolveAccountId(final Link<?> link) {
    Objects.requireNonNull(link);

    if (link.isConnected()) {
      return AccountId.of(link.getLinkId().value());
    } else {
      // If a Plugin is disconnected, then throw an exception.
      throw new RuntimeException("Disconnected Plugins do not have an associated account!");

      //      if (link instanceof LoopbackPlugin) {
      //        // Connected Btp Plugins will have a BTP Session that can be used to get the accountId.
      //        final LoopbackPlugin loopbackPlugin = (LoopbackPlugin) link;
      //        return AccountId.of(loopbackPlugin.getPluginId().get().value());
      //      }
      //      if (link instanceof AbstractBtpPlugin) {
      //        // Connected Btp Plugins will have a BTP Session that can be used to get the accountId.
      //        final AbstractBtpPlugin abstractBtpPlugin = (AbstractBtpPlugin) link;
      //        return this.resolveAccountId(abstractBtpPlugin.getBtpSessionCredentials());
      //      }
      //      if (link instanceof PingProtocolPlugin) {
      //        // Connected Btp Plugins will have a BTP Session that can be used to get the accountId.
      //        final PingProtocolPlugin pingProtocolPlugin = (PingProtocolPlugin) link;
      //        return this.resolveAccountId(pingProtocolPlugin.getPluginId().get());
      //      } else {
      //        throw new RuntimeException("Unsupported Plugin Class: " + link.getClass());
      //      }
    }
  }

  /**
   * Determine the {@link AccountId} for the supplied plugin.
   *
   * @param btpSession The {@link BtpSession} to introspect to determine the accountId that it represents.
   *
   * @return The {@link AccountId} for the supplied plugin.
   */
  @Override
  public AccountId resolveAccountId(final BtpSession btpSession) {
    Objects.requireNonNull(btpSession);

    return btpSession.getBtpSessionCredentials()
      .map(this::resolveAccountId)
      .orElseThrow(() -> new RuntimeException("No BtpSessionCredentials found!"));
  }

  /**
   * Determine the {@link AccountId} for the supplied plugin.
   *
   * @param btpSessionCredentials The {@link BtpSession} to introspect to determine the accountId that it represents.
   *
   * @return The {@link AccountId} for the supplied plugin.
   */
  @VisibleForTesting
  protected AccountId resolveAccountId(final BtpSessionCredentials btpSessionCredentials) {
    Objects.requireNonNull(btpSessionCredentials);

    return btpSessionCredentials.getAuthUsername()
      .map(AccountId::of)
      .orElseGet(() -> {
        // No AuthUserName, so get the AuthToken and hash it.
        //Route.HMAC(abstractBtpPlugin.getBtpSessionCredentials().getAuthToken());
        throw new RuntimeException("Not yet implemented!");
      });
  }

  @Override
  public AccountId resolveAccountId(final Authentication authentication) {
    Objects.requireNonNull(authentication);
    return AccountId.of(authentication.getPrincipal().toString());
  }
}
