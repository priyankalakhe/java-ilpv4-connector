package org.interledger.connector.persistence.converters;

import okhttp3.HttpUrl;
import org.interledger.connector.accounts.SettlementEngineAccountId;
import org.interledger.connector.accounts.SettlementEngineDetails;
import org.interledger.connector.persistence.converters.SettlementEngineDetailsEntityConverter;
import org.interledger.connector.persistence.entities.SettlementEngineDetailsEntity;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit tests for {@link SettlementEngineDetailsEntityConverter}.
 */
public class SettlementEngineDetailsEntityConverterTest {

  private SettlementEngineDetailsEntityConverter converter;

  @Before
  public void setUp() {
    this.converter = new SettlementEngineDetailsEntityConverter();
  }

  @Test
  public void convert() {
    final SettlementEngineDetails settlementEngineDetails = SettlementEngineDetails.builder()
      .baseUrl(HttpUrl.parse("https://example.com"))
      .settlementEngineAccountId(SettlementEngineAccountId.of(UUID.randomUUID().toString()))
      .putCustomSettings("xrpAddress", "rsWs4m35EJctu7Go3FydVwQeGdMQX96XLH")
      .build();
    final SettlementEngineDetailsEntity entity = new SettlementEngineDetailsEntity(settlementEngineDetails);

    SettlementEngineDetails actual = converter.convert(entity);

    assertThat(actual.baseUrl(), is(settlementEngineDetails.baseUrl()));
    assertThat(actual.settlementEngineAccountId(), is(settlementEngineDetails.settlementEngineAccountId()));
    assertThat(actual.customSettings().get("xrpAddress"), is("rsWs4m35EJctu7Go3FydVwQeGdMQX96XLH"));
  }
}
