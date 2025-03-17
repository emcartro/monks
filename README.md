Prerequisites:
1. Clone the repository from  https://github.com/emcartro/monks.git
2. Compile the Java files:  javac Main.java

Implementation Notes

## Data Structures Used
* orderMap: A HashMap to store orders by their unique orderId.
* securityOrdersMap: A HashMap to store lists of orders indexed by securityId. This facilitates quick access to all orders
for a specific security.
* userOrdersMap: A HashMap to store lists of orders indexed by user. This enables efficient cancellation of all orders for a specific user.

## Concurrency Handling
*  In the cancelOrdersForUser method, the code iterates over a copy of the `userOrders` list (`new ArrayList<>(userOrders)`) to avoid `ConcurrentModificationException` when removing elements from the original list.
*  In the cancelOrdersForSecIdWithMinimumQty method, the code uses an `Iterator` to safely remove elements from the list while iterating.
