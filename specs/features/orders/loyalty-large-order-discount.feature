@REQ-ORD-017 @api
Feature: Loyalty discount on large orders
  As a loyalty member
  I want a bigger discount on large orders
  So that I am rewarded for buying more

  # Decisions recorded with the business (issue #4):
  # - The 15% applies to the WHOLE order total, not only the part above 1000.00.
  #   Consequently an order of 1000.01 pays less than an order of 1000.00
  #   (850.01 vs 900.00). This is intended, per "15% instead of 10%".
  # - All examples stay below the REQ-ORD-014 order value limit (10000.00 EUR).

  Rule: Loyalty members get 15% off orders strictly above 1000.00 EUR (rounded half-up to cents)

    @AC-ORD-017-1 @smoke
    Scenario: Loyalty member with a large order gets the bigger discount
      Given a loyalty customer
      When they place an order totalling 2000.00 EUR
      Then the payable amount is 1700.00 EUR

    @AC-ORD-017-2
    Scenario Outline: Large-order threshold and rounding boundaries
      Given a <membership> customer
      When they place an order totalling <total> EUR
      Then the payable amount is <payable> EUR

      Examples: Just above the threshold and half-up rounding
        | membership | total   | payable |
        | loyalty    | 1000.01 | 850.01  |
        | loyalty    | 1000.10 | 850.09  |
        | loyalty    | 1234.50 | 1049.33 |
        | loyalty    | 9999.99 | 8499.99 |

  Rule: Loyalty orders above 100.00 up to and including 1000.00 EUR keep 10% off

    @AC-ORD-017-3
    Scenario Outline: Loyalty orders at or just below the large-order threshold keep 10% off
      Given a <membership> customer
      When they place an order totalling <total> EUR
      Then the payable amount is <payable> EUR

      Examples: At and just below the threshold
        | membership | total   | payable |
        | loyalty    | 1000.00 | 900.00  |
        | loyalty    | 999.99  | 899.99  |
        | loyalty    | 500.05  | 450.05  |

  Rule: Standard customers never get a discount

    @AC-ORD-017-4
    Scenario Outline: Standard customers pay the full amount on large orders
      Given a <membership> customer
      When they place an order totalling <total> EUR
      Then the payable amount is <payable> EUR

      Examples: Standard customers above the large-order threshold
        | membership | total   | payable |
        | standard   | 1000.01 | 1000.01 |
        | standard   | 1000.10 | 1000.10 |
        | standard   | 2000.00 | 2000.00 |
