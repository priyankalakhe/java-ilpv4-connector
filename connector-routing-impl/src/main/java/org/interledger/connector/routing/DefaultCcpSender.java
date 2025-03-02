package org.interledger.connector.routing;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.interledger.connector.accounts.AccountId;
import org.interledger.connector.accounts.AccountSettings;
import org.interledger.connector.ccp.CcpConstants;
import org.interledger.connector.ccp.CcpNewRoute;
import org.interledger.connector.ccp.CcpRouteControlRequest;
import org.interledger.connector.ccp.CcpRouteUpdateRequest;
import org.interledger.connector.ccp.CcpSyncMode;
import org.interledger.connector.ccp.CcpWithdrawnRoute;
import org.interledger.connector.ccp.ImmutableCcpNewRoute;
import org.interledger.connector.ccp.ImmutableCcpRoutePathPart;
import org.interledger.connector.ccp.ImmutableCcpRouteUpdateRequest;
import org.interledger.connector.ccp.ImmutableCcpWithdrawnRoute;
import org.interledger.connector.link.Link;
import org.interledger.connector.persistence.repositories.AccountSettingsRepository;
import org.interledger.connector.settings.ConnectorSettings;
import org.interledger.core.InterledgerPreparePacket;
import org.interledger.encoding.asn.framework.CodecContext;

import com.google.common.primitives.UnsignedLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

import javax.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A default implementation of {@link CcpSender}.
 */
public class DefaultCcpSender implements CcpSender {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final Supplier<ConnectorSettings> connectorSettingsSupplier;
  private final ForwardingRoutingTable<RouteUpdate> forwardingRoutingTable;
  private final AccountSettingsRepository accountSettingsRepository;
  private final CodecContext ccpCodecContext;

  private final AccountId peerAccountId;
  private final Link link;

  // Next epoch that the peer requested from us.
  private final AtomicInteger lastKnownEpoch;
  private final AtomicReference<CcpSyncMode> syncMode;

  private final AtomicReference<RoutingTableId> lastKnownRoutingTableId;
  private final ConcurrentTaskScheduler scheduler;

  // This holds the scheduled route update task. If nothing is scheduled, then this value will be null.
  private ScheduledFuture<?> scheduledTask;

  /**
   * Required-args Constructor.
   */
  public DefaultCcpSender(
    final Supplier<ConnectorSettings> connectorSettingsSupplier,
    final AccountId peerAccountId,
    final Link link,
    final ForwardingRoutingTable<RouteUpdate> forwardingRoutingTable,
    final AccountSettingsRepository accountSettingsRepository,
    final CodecContext ccpCodecContext
  ) {
    this.peerAccountId = Objects.requireNonNull(peerAccountId);
    this.forwardingRoutingTable = Objects.requireNonNull(forwardingRoutingTable);
    this.accountSettingsRepository = Objects.requireNonNull(accountSettingsRepository);
    this.ccpCodecContext = Objects.requireNonNull(ccpCodecContext);
    this.connectorSettingsSupplier = Objects.requireNonNull(connectorSettingsSupplier);
    this.link = Objects.requireNonNull(link);

    this.syncMode = new AtomicReference<>(CcpSyncMode.MODE_IDLE);

    this.lastKnownEpoch = new AtomicInteger();
    this.lastKnownRoutingTableId = new AtomicReference<>(RoutingTableId.of(UUID.randomUUID()));

    // Since a CcpSender only operates for a single peer, we only need a single thread.
    this.scheduler = new ConcurrentTaskScheduler(Executors.newSingleThreadScheduledExecutor());
  }

  @Override
  public void handleRouteControlRequest(final CcpRouteControlRequest routeControlRequest) {
    Preconditions.checkNotNull(link, "Link must be assigned before using a CcpSender!");

    logger.debug("Peer {} sent CcpRouteControlRequest: {}", peerAccountId, routeControlRequest);
    if (syncMode.get() != routeControlRequest.mode()) {
      logger.debug(
        "Peer {} requested changing routing mode. oldMode={} newMode={}",
        peerAccountId, this.syncMode.get(), routeControlRequest.mode()
      );
    }

    this.syncMode.set(routeControlRequest.mode());

    if (lastKnownRoutingTableId.get().equals(this.forwardingRoutingTable.getRoutingTableId()) == false) {
      logger.debug(
        "Peer {} has old routing table id, resetting lastKnownEpoch to 0. theirTableId={} correctTableId={}",
        peerAccountId,
        lastKnownRoutingTableId,
        this.forwardingRoutingTable.getRoutingTableId());
      this.lastKnownEpoch.set(0);
    } else {
      logger.debug("Peer epoch set. peerAccountId={} lastKnownEpoch={} currentEpoch={}",
        peerAccountId,
        lastKnownEpoch,
        this.forwardingRoutingTable.getCurrentEpoch()
      );
      this.lastKnownEpoch.set(routeControlRequest.lastKnownEpoch());
    }

    /////////////////
    // We don't support any optional features, so we ignore the `features`
    /////////////////

    // Only startBroadcasting broadcasting if the sync-mode is MODE_SYNC.
    if (this.syncMode.get() == CcpSyncMode.MODE_SYNC) {
      // Start broadcasting routes to this peer, but only if another thread isn't already pending...
      this.startBroadcasting();
    } else {
      // Stop broadcasting routes to this peer.
      this.stopBroadcasting();
    }
  }

  public void startBroadcasting() {

    // Start broadcasting routes to the configured peer, but only if another thread isn't already pending...
    // Synchronize on this instance so that only a single thread at a time can enter this block...
    synchronized (this) {
      if (scheduledTask == null) {
        scheduledTask = this.scheduler.scheduleWithFixedDelay(
          this::sendRouteUpdateRequest,
          this.connectorSettingsSupplier.get().globalRoutingSettings().routeBroadcastInterval()
        );
        logger.info("CcpSender now broadcasting to Peer: {}", this.peerAccountId);
      } else {
        // Do nothing. The route-update has already been scheduled...
      }
    }
  }

  @PreDestroy
  public void stopBroadcasting() {
    // Stop broadcasting routes to the configured peer.
    // Synchronize on this instance so that only a single thread at a time can enter this block...
    synchronized (this) {
      try {
        if (this.scheduledTask != null) {
          this.scheduledTask.cancel(false);
          logger.info("CcpSender no longer broadcasting to Peer: {}", this.peerAccountId);
        } else {
          // Do nothing. There is no scheduled task to cancel.
        }
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
      this.scheduledTask = null;
    }
  }

  /**
   * Send a route update to a remote peer.
   */
  // @Async -- No need to manage this thread via Spring because this operation is manually scheduled in
  // response to route control requests.
  public void sendRouteUpdateRequest() {
    Preconditions.checkNotNull(link, "Link must be assigned before using a CcpSender!");

    // Perform route update, catch and log any exceptions...
    try {
      final int nextRequestedEpoch = this.lastKnownEpoch.get();

      // Find all updates from the nextRequestedEpoch index to the (nextRequestedEpoch + maxPerTable) inside of the
      // routing table's Log. These are the udpates that should be sent to the remote peer.

      int skip = nextRequestedEpoch;
      int limit = 0;
      // TODO:FIXME
      //        (int) (nextRequestedEpoch + this.connectorSettingsSupplier.get().getRouteBroadcastSettings()
      //          .maxEpochsPerRoutingTable());
      final Iterable<RouteUpdate> allUpdatesToSend = this.forwardingRoutingTable.getPartialRouteLog(skip, limit);

      // Despite asking for N updates, there may not be that many to send, so compute the `toEpoch` properly.
      final int toEpoch =
        nextRequestedEpoch + (int) StreamSupport.stream(allUpdatesToSend.spliterator(), false).count();

      // Filter the List....
      final List<RouteUpdate> filteredUpdatesToSend = StreamSupport.stream(allUpdatesToSend.spliterator(), false)
        .map(routeUpdate -> {

          // If there are no routes in the update, then skip it...
          if (!routeUpdate.route().isPresent()) {
            return routeUpdate;
          } else {
            final Route actualRoute = routeUpdate.route().get();
            // Don't send peer their own routes (i.e., withdraw this route)
            if (actualRoute.nextHopAccountId().equals(peerAccountId)) {
              return null;
            }

            // Don't advertise Peer or Supplier (Parent) routes to Suppliers (Parents).
            final boolean nextHopRelationIsPeerOrParent = this
              .accountSettingsRepository.findByAccountIdWithConversion(actualRoute.nextHopAccountId())
              .map(AccountSettings::isPeerOrParentAccount)
              .orElseGet(() -> {
                logger.error("NextHop Route {} was not found in the PeerManager!", actualRoute.nextHopAccountId());
                return false;
              });

            final boolean thisLinkIsParent = this
              .accountSettingsRepository.findByAccountIdWithConversion(peerAccountId)
              .map(AccountSettings::isParentAccount)
              .orElse(false);

            if (thisLinkIsParent || nextHopRelationIsPeerOrParent) {
              // If the current link is our parent; OR, if the next-hop is a peer or Parent, then withdraw the
              // route. We only advertise routes to peers/children where the next-hop is a child.
              return null;
            } else {
              return routeUpdate;
            }
          }
        })
        .collect(Collectors.toList());

      final ImmutableList.Builder<CcpNewRoute> newRoutesBuilder = ImmutableList.builder();
      final ImmutableList.Builder<CcpWithdrawnRoute> withdrawnRoutesBuilder = ImmutableList.builder();

      // Populate newRoutesBuilder....
      filteredUpdatesToSend.stream()
        .filter(routeUpdate -> routeUpdate.route().isPresent())
        .map(RouteUpdate::route)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(routingTableEntry ->
          ImmutableCcpNewRoute.builder()
            .prefix(routingTableEntry.routePrefix())
            .auth(routingTableEntry.auth())
            .path(
              routingTableEntry.path().stream()
                .map(address -> ImmutableCcpRoutePathPart.builder()
                  .routePathPart(address)
                  .build()
                )
                .collect(Collectors.toList())
            )
            .build()
        ).forEach(newRoutesBuilder::add);

      // Populate withdrawnRoutesBuilder....
      filteredUpdatesToSend.stream()
        .filter(routeUpdate -> !routeUpdate.route().isPresent())
        .map(routingTableEntry ->
          ImmutableCcpWithdrawnRoute.builder()
            .prefix(routingTableEntry.routePrefix())
            .build()
        ).forEach(withdrawnRoutesBuilder::add);

      // Construct RouteUpdateRequest
      final CcpRouteUpdateRequest ccpRouteUpdateRequest = ImmutableCcpRouteUpdateRequest.builder()
        .speaker(this.connectorSettingsSupplier.get().operatorAddressSafe())
        .routingTableId(this.forwardingRoutingTable.getRoutingTableId())
        .holdDownTime(this.connectorSettingsSupplier.get().globalRoutingSettings().routeExpiry().toMillis())
        .currentEpochIndex(this.forwardingRoutingTable.getCurrentEpoch())
        .fromEpochIndex(this.lastKnownEpoch.get())
        .toEpochIndex(toEpoch)
        .newRoutes(newRoutesBuilder.build())
        .withdrawnRoutePrefixes(withdrawnRoutesBuilder.build())
        .build();

      // Try to send the ccpRouteUpdateRequest....

      // We anticipate that they're going to be happy with our route update and ccpRouteUpdateRequest the next one.
      final int previousNextRequestedEpoch = this.lastKnownEpoch.get();
      this.lastKnownEpoch.compareAndSet(previousNextRequestedEpoch, toEpoch);

      final InterledgerPreparePacket preparePacket = InterledgerPreparePacket.builder()
        .amount(UnsignedLong.ZERO)
        .destination(CcpConstants.CCP_UPDATE_DESTINATION_ADDRESS)
        .executionCondition(CcpConstants.PEER_PROTOCOL_EXECUTION_CONDITION)
        .expiresAt(Instant.now().plus(
          // TODO: Verify this is correct. Should the packet just have a normal expiration?
          this.connectorSettingsSupplier.get().globalRoutingSettings().routeExpiry()
        ))
        .data(serializeCcpPacket(ccpRouteUpdateRequest))
        .build();
      logger.info(
        "CcpSender sending RouteUpdate Request: targetPeerAccountId={}. ccpRouteUpdateRequest={} preparePacket={}",
        this.peerAccountId, ccpRouteUpdateRequest, preparePacket
      );
      
      this.link.sendPacket(preparePacket).handle(fulfillPacket -> {
        logger.debug("Route update succeeded. targetPeerAccountId={} fulfillPacket={}", peerAccountId, fulfillPacket);
      }, rejectPacket -> {
        logger.error("Route update failed! targetPeerAccountId={} rejectPacket={}", peerAccountId, rejectPacket);
      });

    } catch (RuntimeException e) {
      logger.error("Failed to broadcast route information to peer. targetPeerAccountId={}", peerAccountId, e);
      throw e;
    }
  }

  @VisibleForTesting
  protected byte[] serializeCcpPacket(final CcpRouteUpdateRequest ccpRouteUpdateRequest) {
    Objects.requireNonNull(ccpRouteUpdateRequest);

    try {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      ccpCodecContext.write(ccpRouteUpdateRequest, outputStream);
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  public CcpSyncMode getSyncMode() {
    return syncMode.get();
  }

  @VisibleForTesting
  protected ForwardingRoutingTable<RouteUpdate> getForwardingRoutingTable() {
    return this.forwardingRoutingTable;
  }
}
