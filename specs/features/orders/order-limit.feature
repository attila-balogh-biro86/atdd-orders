@REQ-ORD-014 @api
Feature: Order value limit
  To limit fraud exposure, very large orders must go through the sales desk instead.

  # STATUS: specified, not yet implemented.
  # Listed in delivery/pending-requirements.txt, so the main pipeline skips it
  # until an implementation PR removes it from that list.
  # This is the demo backlog item: ask Claude Code to `/implement REQ-ORD-014`.

  Rule: Orders above 10000.00 EUR are rejected; exactly 10000.00 is allowed

    @AC-ORD-014-1
    Scenario: Order at the limit is accepted
      Given a standard customer
      When they place an order totalling 10000.00 EUR
      Then the payable amount is 10000.00 EUR

    @AC-ORD-014-2
    Scenario Outline: Orders above the limit are rejected
      Given a <membership> customer
      When they place an order totalling <total> EUR
      Then the order is rejected because "must not exceed 10000.00"

      Examples:
        | membership | total    |
        | standard   | 10000.01 |
        | loyalty    | 25000.00 |
