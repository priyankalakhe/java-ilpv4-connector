# ILPv4 Connector Performance Testing
This document describes a standardized load-test suite that can be used to guage the performance of an ILPv4 
Connector implementation.

## Performance Testing Considerations
Testing a connector involves several facets of the overall Interledger payment protocol stack. For example, it's 
useful to measure each of the following metrics:

1. Packets-per-second (i.e., how many Prepare/Fulfill requests can a Connector process over a given period of time). 

These tests also encompass different dimensions that should be considered in order to get a good picture of overeall 
performance, such as:

1. Theoretical vs Actual performance (i.e., a single Connector with Loopback plugins vs a single Connector sending 
payments down a multi-hop path involving one or more other connectors).
1. Fulfill vs Reject paths (i.e., the fulfill path involves more processing overhead, and is expected to be a bit 
slower than rejections)
1. Currency conversion (every currency conversion involves mutating an ILP Prepare packet in order to send to the 
next hop)

## Performance Testing Non-Goals
For this performance test, a non-goal is the testing of Payments-per-second, such using one or more Stream payments. 
This type of testing will simply produce many varied ILPv4 packet flows, so if a Connector is optimizing for this 
kind of traffic, then STREAM payments should become better as the Connector is optimized.

# Performance Test Designs
This harness utilizes three different topologies for its testing:

1. *Single Connector with Loopback Links*: This type of topology involves only a single connector with 3 loopback 
links. The first link always fulfills a prepare packet; the second link always rejects a prepare packet, and the 
third link returns a `T02 Peer Busy` response to simulate a peer link that is being throttled.

1. *Two Connectors with ILP-over-HTTP*: This type of topology involves only a two connectors, each with a single 
ILP-over-HTTP link. The test sends real ILP prepare packets and expects actual fulfillments back throughout the 
entire test.

1. *4 Connector Multihop*: This topology simulates four connectors (3 hops) with each operating a BLAST 
(ILP-over-HTTP) link simulating three total currency conversions. The test sends real ILP prepare packets and expects 
actual fulfillments back throughout the duration of the test.