@REQ-ORD-013 @api
Feature: Order validation
  Orders must carry a valid amount and belong to a known customer.

  Rule: The order total must be a positive amount with at most two decimals

    @AC-ORD-013-1
    Scenario Outline: Invalid totals are rejected
      Given a standard customer
      When they place an order totalling <total> EUR
      Then the order is rejected because "<reason>"

      Examples:
        | total  | reason                              |
        | 0.00   | must be greater than 0              |
        | -5.00  | must be greater than 0              |
        | 10.001 | must have at most 2 decimals        |

  Rule: Orders can only be placed by registered customers

    @AC-ORD-013-2
    Scenario: Unknown customer
      Given an unregistered customer
      When they place an order totalling 50.00 EUR
      Then the order is rejected because the customer is unknown
