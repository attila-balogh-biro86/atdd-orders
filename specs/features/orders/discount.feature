@REQ-ORD-012 @api
Feature: Loyalty discount
  As a loyalty member
  I want a discount on larger orders
  So that I am rewarded for shopping with us

  Rule: Loyalty members get 10% off orders strictly above 100.00 EUR (rounded half-up to cents)

    @AC-ORD-012-1 @smoke
    Scenario: Loyalty member above the threshold gets the discount
      Given a loyalty customer
      When they place an order totalling 200.00 EUR
      Then the payable amount is 180.00 EUR

    @AC-ORD-012-2
    Scenario Outline: Threshold and rounding boundaries
      Given a <membership> customer
      When they place an order totalling <total> EUR
      Then the payable amount is <payable> EUR

      Examples: Loyalty boundary
        | membership | total  | payable |
        | loyalty    | 100.00 | 100.00  |
        | loyalty    | 100.01 | 90.01   |
        | loyalty    | 100.05 | 90.05   |
        | loyalty    | 133.35 | 120.02  |

      Examples: Standard customers never get the discount
        | membership | total   | payable |
        | standard   | 100.01  | 100.01  |
        | standard   | 5000.00 | 5000.00 |
