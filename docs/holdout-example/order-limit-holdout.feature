# EXAMPLE ONLY. In a real setup this file lives in a PRIVATE repo (vars.HOLDOUT_REPO, folder features/)
# that implementers and implementation agents cannot read. CI mounts it at specs/features/_holdout/
# for implementation PRs and main. It uses the same step language, so no extra glue is needed.
#
# Why: an agent that overfits to the visible Examples (e.g. `if total == 10000.01`) passes the
# public scenarios but fails these.

@REQ-ORD-014 @holdout
Feature: Order value limit (holdout examples)

  Scenario Outline: Unseen values around the limit
    Given a <membership> customer
    When they place an order totalling <total> EUR
    Then <outcome>

    Examples:
      | membership | total    | outcome                                                  |
      | loyalty    | 9999.99  | the payable amount is 8999.99 EUR                       |
      | loyalty    | 10000.00 | the payable amount is 9000.00 EUR                       |
      | standard   | 10000.02 | the order is rejected because "must not exceed 10000.00" |
