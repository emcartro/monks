package orders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

class Order {

    private final String orderId;
    private final String securityId;
    private final String side;
    private final int qty;
    private final String user;
    private final String company;

    // Do not alter the signature of this constructor
    public Order(String orderId, String securityId, String side, int qty, String user, String company) {
        this.orderId = orderId;
        this.securityId = securityId;
        this.side = side;
        this.qty = qty;
        this.user = user;
        this.company = company;
    }

    // Do not alter these accessor methods
    public String getOrderId() { return orderId; }
    public String getSecurityId() { return securityId; }
    public String getSide() { return side; }
    public int getQty() { return qty; }
    public String getUser() { return user; }
    public String getCompany() { return company; }


    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId +
                ", securityId='" + securityId + '\'' +
                ", qty=" + qty +
                '}';
    }
}

// Provide an implementation for the OrderCacheInterface interface.
// Your implementation class should hold all relevant data structures you think
// are needed.
interface OrderCacheInterface {

    // Implement the 6 methods below, do not alter signatures

    // Add order to the cache
    void addOrder(Order order);

    // Remove order with this unique order id from the cache
    void cancelOrder(String orderId);

    // Remove all orders in the cache for this user
    void cancelOrdersForUser(String user);

    // Remove all orders in the cache for this security with qty >= minQty
    void cancelOrdersForSecIdWithMinimumQty(String securityId, int minQty);

    // Return the total qty that can match for the security id
    int getMatchingSizeForSecurity(String securityId);

    // Return all orders in cache as a list
    List<Order> getAllOrders();
}



class OrderCache implements OrderCacheInterface {
    private final Map<String, Order> orderMap = new HashMap<>();
    private final Map<String, List<Order>> securityOrdersMap = new HashMap<>();
    private final Map<String, List<Order>> userOrdersMap = new HashMap<>();

    @Override
    public void addOrder(Order order) {

        orderMap.put(order.getOrderId(), order);
        securityOrdersMap.computeIfAbsent(order.getSecurityId(), k -> new ArrayList<>()).add(order);
        userOrdersMap.computeIfAbsent(order.getUser(), k -> new ArrayList<>()).add(order);
    }

    @Override
    public void cancelOrder(String orderId) {
        Order order = orderMap.remove(orderId);
        if (order != null) {
            securityOrdersMap.get(order.getSecurityId()).remove(order);
            userOrdersMap.get(order.getUser()).remove(order);
        }
    }

    @Override
    public void cancelOrdersForUser(String user) {
        List<Order> userOrders = userOrdersMap.remove(user);
        if (userOrders != null) {
            for (Order order : new ArrayList<>(userOrders)) { //With larger data I was getting a ConcurrentModificationException
                orderMap.remove(order.getOrderId());
                securityOrdersMap.get(order.getSecurityId()).remove(order);
            }
        }
    }

    @Override
    public void cancelOrdersForSecIdWithMinimumQty(String securityId, int minQty) {

        List<Order> ordersForSecurity = securityOrdersMap.getOrDefault(securityId, new ArrayList<>());
        List<Order> ordersToRemove = new ArrayList<>();


        for (Order order : ordersForSecurity) {
            if (order.getQty() >= minQty) {
                ordersToRemove.add(order);
            }
        }
        for (Order orderToRemove : ordersToRemove) {
            cancelOrder(orderToRemove.getOrderId());
        }
    }

    @Override
    public int getMatchingSizeForSecurity(String securityId) {

        List<Order> ordersForSecurity = securityOrdersMap.getOrDefault(securityId, new ArrayList<>());
        // separate buy and sell orders
        List<Order> buyOrders = new ArrayList<>();
        List<Order> sellOrders = new ArrayList<>();

        for (Order order : ordersForSecurity) {
            if ("Buy".equalsIgnoreCase(order.getSide())) {
                buyOrders.add(order);
            } else if ("Sell".equalsIgnoreCase(order.getSide())) {
                sellOrders.add(order);
            }
        }

        int totalMatchQty = 0;

        //Iterate over the buy orders
        for (Order buyOrder : new ArrayList<>(buyOrders)) {
            int buyQty = buyOrder.getQty();
            // Iterate over the sell orders
            for (Order sellOrder : new ArrayList<>(sellOrders)) {
                // Verify matching security
                if (!buyOrder.getCompany().equals(sellOrder.getCompany())) {
                    int sellQty = sellOrder.getQty();
                    int matchQty = Math.min(buyQty, sellQty);
                    totalMatchQty += matchQty;
                    buyQty -= matchQty;
                    sellQty -= matchQty;

                    // Update the buy order in the list if there is remaining quantity
                    if (buyQty == 0) {
                        buyOrders.remove(buyOrder);
                    } else {
                        int index = buyOrders.indexOf(buyOrder);
                        if (index != -1) {
                            buyOrders.set(index, new Order(buyOrder.getOrderId(), buyOrder.getSecurityId(), buyOrder.getSide(), buyQty, buyOrder.getUser(), buyOrder.getCompany()));
                        }
                    }

                    // Update the sell order in the list if there is remaining quantity
                    if (sellQty == 0) {
                        sellOrders.remove(sellOrder);
                    } else {
                        int index = sellOrders.indexOf(sellOrder);
                        if (index != -1) {
                            sellOrders.set(index, new Order(sellOrder.getOrderId(), sellOrder.getSecurityId(), sellOrder.getSide(), sellQty, sellOrder.getUser(), sellOrder.getCompany()));
                        }
                    }

                    // If buy order quantity is completed, go to next
                    if (buyQty == 0) {
                        break;
                    }
                }
            }
        }
        return totalMatchQty;
    }

    @Override
    public List<Order> getAllOrders() {
        return new ArrayList<>(orderMap.values());
    }
}

class OrderCacheThreadSafe implements OrderCacheInterface {
    private final ConcurrentHashMap<String, Order> orderMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Order>> securityOrdersMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Order>> userOrdersMap = new ConcurrentHashMap<>();

    @Override
    public void addOrder(Order order) {
        orderMap.put(order.getOrderId(), order);
        securityOrdersMap.computeIfAbsent(order.getSecurityId(), k -> new CopyOnWriteArrayList<>()).add(order);
        userOrdersMap.computeIfAbsent(order.getUser(), k -> new CopyOnWriteArrayList<>()).add(order);
    }

    @Override
    public void cancelOrder(String orderId) {
        Order order = orderMap.remove(orderId);
        if (order != null) {
            securityOrdersMap.get(order.getSecurityId()).remove(order);
            userOrdersMap.get(order.getUser()).remove(order);
        }
    }

    @Override
    public void cancelOrdersForUser(String user) {
        CopyOnWriteArrayList<Order> userOrders = userOrdersMap.remove(user);
        if (userOrders != null) {
            for (Order order : userOrders) {
                orderMap.remove(order.getOrderId());
                securityOrdersMap.get(order.getSecurityId()).remove(order);
            }
        }
    }

    @Override
    public void cancelOrdersForSecIdWithMinimumQty(String securityId, int minQty) {
        CopyOnWriteArrayList<Order> ordersForSecurity = securityOrdersMap.getOrDefault(securityId, new CopyOnWriteArrayList<>());
        for (Order order : ordersForSecurity) {
            if (order.getQty() >= minQty) {
                cancelOrder(order.getOrderId());
            }
        }
    }

    @Override
    public int getMatchingSizeForSecurity(String securityId) {
        CopyOnWriteArrayList<Order> ordersForSecurity = securityOrdersMap.getOrDefault(securityId, new CopyOnWriteArrayList<>());
        List<Order> buyOrders = new ArrayList<>();
        List<Order> sellOrders = new ArrayList<>();

        // Separate buy and sell orders
        for (Order order : ordersForSecurity) {
            if ("Buy".equalsIgnoreCase(order.getSide())) {
                buyOrders.add(order);
            } else if ("Sell".equalsIgnoreCase(order.getSide())) {
                sellOrders.add(order);
            }
        }

        int totalMatchQty = 0;

        // Iterate over the buy orders
        for (Order buyOrder : buyOrders) {
            int buyQty = buyOrder.getQty();
            // Iterate over the sell orders
            for (int i = 0; i < sellOrders.size(); i++) {
                Order sellOrder = sellOrders.get(i);
                // Verify matching security and different companies
                if (!buyOrder.getCompany().equals(sellOrder.getCompany())) {
                    int sellQty = sellOrder.getQty();
                    int matchQty = Math.min(buyQty, sellQty);
                    totalMatchQty += matchQty;
                    buyQty -= matchQty;
                    sellQty -= matchQty;

                    // Update the sell order in the list if there is remaining quantity
                    if (sellQty > 0) {
                        sellOrders.set(i, new Order(
                                sellOrder.getOrderId(),
                                sellOrder.getSecurityId(),
                                sellOrder.getSide(),
                                sellQty,
                                sellOrder.getUser(),
                                sellOrder.getCompany()
                        ));
                    } else {
                        sellOrders.remove(i);
                        i--; // Adjust index after removal
                    }

                    // If buy order quantity is completed, go to next
                    if (buyQty == 0) {
                        break;
                    }
                }
            }
        }

        return totalMatchQty;
    }



    @Override
    public List<Order> getAllOrders() {
        return new ArrayList<>(orderMap.values());
    }
}
class Main {
    public static void main(String[] args) {
        OrderCache cache = new OrderCache();
         // Example 1: Order Matching Example
        System.out.println("=== Example 1: Order Matching Example ===");
        cache.addOrder(new Order("OrdId1", "SecId1", "Buy", 1000, "User1", "CompanyA"));
        cache.addOrder(new Order("OrdId2", "SecId2", "Sell", 3000, "User2", "CompanyB"));
        cache.addOrder(new Order("OrdId3", "SecId1", "Sell", 500, "User3", "CompanyA"));
        cache.addOrder(new Order("OrdId4", "SecId2", "Buy", 600, "User4", "CompanyC"));
        cache.addOrder(new Order("OrdId5", "SecId2", "Buy", 100, "User5", "CompanyB"));
        cache.addOrder(new Order("OrdId6", "SecId3", "Buy", 1000, "User6", "CompanyD"));
        cache.addOrder(new Order("OrdId7", "SecId2", "Buy", 2000, "User7", "CompanyE"));
        cache.addOrder(new Order("OrdId8", "SecId2", "Sell", 5000, "User8", "CompanyE"));

        // Get matching size for SecId1, SecId2, and SecId3
        System.out.println("Matching size for SecId1 (Expected: 0): " + cache.getMatchingSizeForSecurity("SecId1"));
        System.out.println("Matching size for SecId2( Expected: 2700): " + cache.getMatchingSizeForSecurity("SecId2"));
        System.out.println("Matching size for SecId3 ( Expected: 0) : " + cache.getMatchingSizeForSecurity("SecId3"));


        System.out.println("Orders before cancelOrdersForUser(\"User2\"): " + cache.getAllOrders());
        cache.cancelOrdersForUser("User2");
        System.out.println("Orders after cancelOrdersForUser(\"User2\"): " + cache.getAllOrders());


        cache.cancelOrdersForSecIdWithMinimumQty("SecId2",2000);
        System.out.println("Orders after cancelOrdersForSecIdWithMinimumQty(\"SecId2\",2000): " + cache.getAllOrders());



        OrderCacheThreadSafe cacheThreadSafe = new OrderCacheThreadSafe();

        // Example 1: Order Matching Example
        System.out.println("\n=== Example 1: Order Matching Example  Using OrderCacheThreadSafe===");
        cacheThreadSafe.addOrder(new Order("OrdId1", "SecId1", "Buy", 1000, "User1", "CompanyA"));
        cacheThreadSafe.addOrder(new Order("OrdId2", "SecId2", "Sell", 3000, "User2", "CompanyB"));
        cacheThreadSafe.addOrder(new Order("OrdId3", "SecId1", "Sell", 500, "User3", "CompanyA"));
        cacheThreadSafe.addOrder(new Order("OrdId4", "SecId2", "Buy", 600, "User4", "CompanyC"));
        cacheThreadSafe.addOrder(new Order("OrdId5", "SecId2", "Buy", 100, "User5", "CompanyB"));
        cacheThreadSafe.addOrder(new Order("OrdId6", "SecId3", "Buy", 1000, "User6", "CompanyD"));
        cacheThreadSafe.addOrder(new Order("OrdId7", "SecId2", "Buy", 2000, "User7", "CompanyE"));
        cacheThreadSafe.addOrder(new Order("OrdId8", "SecId2", "Sell", 5000, "User8", "CompanyE"));

        // Get matching size for SecId1, SecId2, and SecId3
        System.out.println("Matching size for SecId1 (Expected: 0): " + cacheThreadSafe.getMatchingSizeForSecurity("SecId1"));
        System.out.println("Matching size for SecId2( Expected: 2700): " + cacheThreadSafe.getMatchingSizeForSecurity("SecId2"));
        System.out.println("Matching size for SecId3 ( Expected: 0) : " + cacheThreadSafe.getMatchingSizeForSecurity("SecId3"));


        System.out.println("Orders before cancelOrdersForUser(\"User2\"): " + cacheThreadSafe.getAllOrders());
        cacheThreadSafe.cancelOrdersForUser("User2");
        System.out.println("Orders after cancelOrdersForUser(\"User2\"): " + cacheThreadSafe.getAllOrders());


        cacheThreadSafe.cancelOrdersForSecIdWithMinimumQty("SecId2",2000);
        System.out.println("Orders after cancelOrdersForSecIdWithMinimumQty(\"SecId2\",2000): " + cacheThreadSafe.getAllOrders());



        // Example 2:

        cacheThreadSafe = new OrderCacheThreadSafe();

        System.out.println("\n=== Example 2: Using OrderCacheThreadSafe ===");
        cacheThreadSafe.addOrder(new Order("OrdId1", "SecId1", "Sell", 100, "User10", "Company2"));
        cacheThreadSafe.addOrder(new Order("OrdId2", "SecId3", "Sell", 200, "User8", "Company2"));
        cacheThreadSafe.addOrder(new Order("OrdId3", "SecId1", "Buy", 300, "User13", "Company2"));
        cacheThreadSafe.addOrder(new Order("OrdId4", "SecId2", "Sell", 400, "User12", "Company2"));
        cacheThreadSafe.addOrder(new Order("OrdId5", "SecId3", "Sell", 500, "User7", "Company2"));
        cacheThreadSafe.addOrder(new Order("OrdId6", "SecId3", "Buy", 600, "User3", "Company1"));
        cacheThreadSafe.addOrder(new Order("OrdId7", "SecId1", "Sell", 700, "User10", "Company2"));
        cacheThreadSafe.addOrder(new Order("OrdId8", "SecId1", "Sell", 800, "User2", "Company1"));
        cacheThreadSafe.addOrder(new Order("OrdId9", "SecId2", "Buy", 900, "User6", "Company2"));
        cacheThreadSafe.addOrder(new Order("OrdId10", "SecId2", "Sell", 1000, "User5", "Company1"));
        cacheThreadSafe.addOrder(new Order("OrdId11", "SecId1", "Sell", 1100, "User13", "Company2"));
        cacheThreadSafe.addOrder(new Order("OrdId12", "SecId2", "Buy", 1200, "User9", "Company2"));
        cacheThreadSafe.addOrder(new Order("OrdId13", "SecId1", "Sell", 1300, "User1", "Company1"));

        // Get matching size for SecId1, SecId2, and SecId3
        System.out.println("Matching size for SecId1 (Expected: 300): " + cacheThreadSafe.getMatchingSizeForSecurity("SecId1"));
        System.out.println("Matching size for SecId2( Expected: 1000): " + cacheThreadSafe.getMatchingSizeForSecurity("SecId2"));
        System.out.println("Matching size for SecId3 ( Expected: 600) : " + cacheThreadSafe.getMatchingSizeForSecurity("SecId3"));




        // Example 3: Additional Example 2
        // cache = new OrderCache();
        cacheThreadSafe = new OrderCacheThreadSafe();

        System.out.println("\n=== Example 3: Using OrderCacheThreadSafe ===");
        cacheThreadSafe.addOrder(new Order("OrdId1", "SecId3", "Sell", 100, "User1", "Company1"));
        cacheThreadSafe.addOrder(new Order("OrdId2", "SecId3", "Sell", 200, "User3", "Company2"));
        cacheThreadSafe.addOrder(new Order("OrdId3", "SecId1", "Buy", 300, "User2", "Company1"));
        cacheThreadSafe.addOrder(new Order("OrdId4", "SecId3", "Sell", 400, "User5", "Company2"));
        cacheThreadSafe.addOrder(new Order("OrdId5", "SecId2", "Sell", 500, "User2", "Company1"));
        cacheThreadSafe.addOrder(new Order("OrdId6", "SecId2", "Buy", 600, "User3", "Company2"));
        cacheThreadSafe.addOrder(new Order("OrdId7", "SecId2", "Sell", 700, "User1", "Company1"));
        cacheThreadSafe.addOrder(new Order("OrdId8", "SecId1", "Sell", 800, "User2", "Company1"));
        cacheThreadSafe.addOrder(new Order("OrdId9", "SecId1", "Buy", 900, "User5", "Company2"));
        cacheThreadSafe.addOrder(new Order("OrdId10", "SecId1", "Sell", 1000, "User1", "Company1"));
        cacheThreadSafe.addOrder(new Order("OrdId11", "SecId2", "Sell", 1100, "User6", "Company2"));

        // Get matching size for SecId1, SecId2, and SecId3
        System.out.println("Matching size for SecId1 (Expected: 900): " + cacheThreadSafe.getMatchingSizeForSecurity("SecId1"));
        System.out.println("Matching size for SecId2( Expected: 600): " + cacheThreadSafe.getMatchingSizeForSecurity("SecId2"));
        System.out.println("Matching size for SecId3 ( Expected: 0) : " + cacheThreadSafe.getMatchingSizeForSecurity("SecId3"));

    }
}

