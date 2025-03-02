package org.interledger.connector.links.loopback;

import org.interledger.connector.packetswitch.PacketRejector;
import org.interledger.connector.link.Link;
import org.interledger.connector.link.LinkFactory;
import org.interledger.connector.link.LinkSettings;
import org.interledger.connector.link.LinkType;
import org.interledger.connector.link.events.LinkEventEmitter;
import org.interledger.core.InterledgerAddress;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * An implementation of {@link LinkFactory} for creating Links that can handle the `Loopback` packets.
 */
public class LoopbackLinkFactory implements LinkFactory {

  private final LinkEventEmitter linkEventEmitter;
  private final PacketRejector packetRejector;

  /**
   * Required-args Constructor.
   */
  public LoopbackLinkFactory(final LinkEventEmitter linkEventEmitter, final PacketRejector packetRejector) {
    this.linkEventEmitter = Objects.requireNonNull(linkEventEmitter);
    this.packetRejector = Objects.requireNonNull(packetRejector);
  }

  /**
   * Construct a new instance of {@link Link} using the supplied inputs.
   *
   * @return A newly constructed instance of {@link Link}.
   */
  public Link<?> constructLink(
    final Supplier<Optional<InterledgerAddress>> operatorAddressSupplier, final LinkSettings linkSettings
  ) {
    Objects.requireNonNull(linkSettings);

    if (!this.supports(linkSettings.linkType())) {
      throw new RuntimeException(
        String.format("LinkType `%s` not supported by this factory!", linkSettings.linkType())
      );
    }

    return new LoopbackLink(operatorAddressSupplier, linkSettings, linkEventEmitter, packetRejector);
  }

  @Override
  public boolean supports(LinkType linkType) {
    return LoopbackLink.LINK_TYPE.equals(linkType);
  }

}
