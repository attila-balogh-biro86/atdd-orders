@REQ-ORD-015 @api
Feature: Order cancellation
  As a customer
  I want to cancel an order
  So that I am not charged for something I no longer want

  # Product owner decisions (issue #1):
  #   - A cancelled order owes nothing: its payable amount becomes 0.00 EUR.
  #   - Every order shows its status (placed or cancelled); a cancelled order can still be looked up.
  #   - There is no ownership check: anyone may cancel an order.
  #   - A placed order can be cancelled at any time; there is no deadline.

  Rule: A newly placed order has status placed

    @AC-ORD-015-1 @smoke
    Scenario Outline: Placing an order gives it status placed
      Given a <membership> customer
      When they place an order totalling <total> EUR
      Then the order has status placed
      And the payable amount is <payable> EUR

      Examples: Status is independent of membership and amount
        | membership | total  | payable |
        | standard   | 50.00  | 50.00   |
        | loyalty    | 200.00 | 180.00  |

  Rule: A placed order can be cancelled; it then has status cancelled and nothing is payable

    @AC-ORD-015-2
    Scenario Outline: Cancelling a placed order
      Given a <membership> customer
      And they place an order totalling <total> EUR
      And the payable amount is <payable before> EUR
      When they cancel the order
      Then the order has status cancelled
      And the payable amount is 0.00 EUR

      Examples: Discounted loyalty order
        | membership | total  | payable before |
        | loyalty    | 200.00 | 180.00         |
        | loyalty    | 100.01 | 90.01          |

      Examples: Standard orders, including the smallest possible amount
        | membership | total   | payable before |
        | standard   | 50.00   | 50.00          |
        | standard   | 0.01    | 0.01           |
        | standard   | 9999.99 | 9999.99        |

    @AC-ORD-015-3
    Scenario: A cancelled order can still be looked up
      Given a loyalty customer
      And they place an order totalling 200.00 EUR
      And they cancel the order
      When they look up the order
      Then the order has status cancelled
      And the payable amount is 0.00 EUR

  Rule: A cancelled order cannot be cancelled again; it stays cancelled

    @AC-ORD-015-4
    Scenario: Cancelling an already cancelled order is rejected
      Given a standard customer
      And they place an order totalling 50.00 EUR
      And they cancel the order
      When they cancel the order
      Then the cancellation is rejected because "already cancelled"

    @AC-ORD-015-5
    Scenario: The order is unchanged after a rejected second cancellation
      Given a loyalty customer
      And they place an order totalling 200.00 EUR
      And they cancel the order
      And they cancel the order
      When they look up the order
      Then the order has status cancelled
      And the payable amount is 0.00 EUR

  Rule: An order that does not exist cannot be cancelled

    @AC-ORD-015-6
    Scenario: Cancelling an unknown order is rejected
      Given a standard customer
      When they cancel an order that does not exist
      Then the cancellation is rejected because the order is unknown

  Rule: Cancelling one order does not affect the customer's other orders

    @AC-ORD-015-7
    Scenario Outline: Only the chosen order is cancelled
      Given a <membership> customer
      And they place an order "A" totalling <total A> EUR
      And they place an order "B" totalling <total B> EUR
      When they cancel order "A"
      Then order "A" has status cancelled
      And order "A" has a payable amount of 0.00 EUR
      And order "B" has status placed
      And order "B" has a payable amount of <payable B> EUR

      Examples: Other order keeps its own payable amount
        | membership | total A | total B | payable B |
        | standard   | 50.00   | 70.00   | 70.00     |
        | loyalty    | 50.00   | 150.00  | 135.00    |
