package org.interledger.connector.settlement.client;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import org.interledger.connector.settlement.client.ImmutableSendMessageRequest;

/**
 * An object for modeling a request to the `/messages` endpoint of a Settlement Engine.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableSendMessageRequest.class)
@JsonDeserialize(as = ImmutableSendMessageRequest.class)
public interface SendMessageRequest {

  static ImmutableSendMessageRequest.Builder builder() {
    return ImmutableSendMessageRequest.builder();
  }

  /**
   * A byte array of data that two Settlement Engines can understand, but for which this client views as opaque data.
   */
  byte[] data();
}
