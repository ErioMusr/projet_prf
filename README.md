# 📦 Akka Distributed Order System - Complete Implementation Guide

## Project Overview

This is a distributed order processing system based on **Akka** and **Scala**, featuring the following core functionalities:

- ✅ **Order Management** (OrderActor)
- ✅ **Inventory Management** (InventoryActor)  
- ✅ **Payment Processing** (PaymentActor)
- ✅ **HTTP Requests** (HttpRequestActor - reserved template)
- ✅ **Data Persistence** (FileStore)

---

## 🎯 Core Features

### 1. Order Processing (OrderActor)

**Responsibility**: Coordinates the order workflow, interacts with Inventory and Payment Actors

**Workflow**:
```
PlaceOrder 
  → Check Inventory (InventoryActor)
    → Stock Available: Process Payment (PaymentActor)
    → Stock Unavailable: Order Failed
  → Handle Payment Result
    → Payment Successful: Order Complete
    → Payment Failed: Order Failed
```

**File**: `src/main/scala/OrderActor.scala`

---

### 2. Inventory Management (InventoryActor)

**Responsibility**: Manages product inventory and verifies availability

**Core Logic**:
- Load initial inventory (or use default values)
- Check if order quantity is available
- If available, reduce inventory and respond ✅
- If insufficient, reject order and respond ❌

**Default Inventory**:
- product-1: 10 units
- product-2: 5 units

**File**: `src/main/scala/InventoryActor.scala`

---

### 3. Payment Processing (PaymentActor) ✨ **NEW**

**Responsibility**: Handles order payments, validates amounts, simulates payment gateway

**Core Logic**:
- Validate payment amount range (0.01 ~ 100,000.00)
- Simulate payment gateway processing (90% success rate)
- Record all transaction information
- Return payment result

**Payment Flow**:
```
ProcessPayment
  ├─ Amount < 0.01       → PaymentFailed (Amount too small)
  ├─ Amount > 100,000    → PaymentFailed (Amount too large)
  ├─ Call Payment Gateway → Random 90% success
  │  ├─ Success: PaymentSuccessful → Save Record
  │  └─ Failed: PaymentFailed     → Save Record
```

**Persistence**: Saved to `payment.txt`
```
orderId:productId:amount:status:timestamp
```

**File**: `src/main/scala/PaymentActor.scala`

---

### 4. Message Protocol (Protocol) ✨ **ENHANCED**

Unified definition of all inter-Actor communication messages

**Core Message Types**:

#### Order Messages
```scala
case class PlaceOrder(productId: String, amount: Int) extends Command
case class InventoryRes(res: InventoryResponse) extends Command
case class PaymentRes(res: PaymentResponse) extends Command
```

#### Inventory Messages
```scala
case class CheckInventoryAmount(
  productId: String, 
  quantity: Int, 
  replyTo: ActorRef[InventoryResponse]
) extends Command

sealed trait InventoryResponse
case class InventoryAvailable(productId: String) extends InventoryResponse
case class InventoryNotEnough(productId: String) extends InventoryResponse
```

#### Payment Messages
```scala
case class ProcessPayment(
  orderId: String, 
  productId: String, 
  amount: Double, 
  replyTo: ActorRef[PaymentResponse]
) extends Command

sealed trait PaymentResponse
case class PaymentSuccessful(orderId: String) extends PaymentResponse
case class PaymentFailed(orderId: String, reason: String) extends PaymentResponse
```

#### HTTP Messages (Reserved)
```scala
case class SendHttpRequest(orderId: String, endpoint: String, data: String) extends Command

sealed trait HttpResponse
case class HttpSuccess(orderId: String, response: String) extends HttpResponse
case class HttpError(orderId: String, error: String) extends HttpResponse
```

**File**: `src/main/scala/Protocol.scala`

---

### 5. Data Persistence (FileStore) ✨ **ENHANCED**

**Functions**:
- Save and load inventory data
- Save and load order status
- Save and load payment records

**Generated Files**:
- `inventory.txt` - Inventory information
- `order.txt` - Order information
- `payment.txt` - Payment records

**File**: `src/main/scala/FlieStore.scala`

---

### 6. HTTP Requests (HttpRequestActor) ✨ **TEMPLATE**

**Reserved** for future expansion with real HTTP functionality

**Basic Structure**:
```scala
// Handle HTTP requests
// Support retry mechanism
// Record request history
// Return success/failure responses
```

**File**: `src/main/scala/HttpRequestActor.scala`

---

### 7. Main Entry Point (Main.scala) ✨ **ENHANCED**

**Startup Process**:
1. Create ActorSystem
2. Initialize all Actors
3. Send test orders
4. Wait for processing to complete
5. View generated file records

**Usage**:
```bash
sbt "runMain Main"
```

**File**: `src/main/scala/Main.scala`

---

## 🚀 Quick Start

### 1. Compile Project
```bash
cd H:\projet_prf
sbt compile
```

### 2. Run System
```bash
sbt run
```

### 3. View Results
- `inventory.txt` - Updated inventory
- `order.txt` - Order status
- `payment.txt` - Payment records

---

## 📊 System Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                   ActorSystem                           │
│  (OrderManagementSystem)                                │
│                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  OrderActor  │  │InventoryActor│  │ PaymentActor │  │
│  │ (Coordinator)│  │ (Inventory)  │  │ (Payments)   │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │
│         │                 │                  │          │
│         │ CheckInventory  │                  │          │
│         ├────────────────→│                  │          │
│         │                 │ Inventory        │          │
│         │←────────────────┤ Response         │          │
│         │                                    │          │
│         │ ProcessPayment                    │          │
│         ├────────────────────────────────────→          │
│         │                                   │          │
│         │← Payment Response                 │          │
│         │                                    │          │
└─────────┼────────────────────────────────────┼──────────┘
          │                                    │
          ↓                                    ↓
       FileStore                           FileStore
    (inventory.txt)                    (payment.txt)
```

---

## 📝 Order Lifecycle

```
State Flow:

INIT
  ↓
STOCK_RESERVED (Stock Reserved)
  ├─→ PAYMENT_SUCCESSFUL (Payment Successful ✅)
  │
  ├─→ PAYMENT_FAILED (Payment Failed ❌)
  │
  └─→ ORDER_FAILED_NO_STOCK (Out of Stock ❌)
```

---

## 🔍 Code Examples

### Send Order
```scala
val orderActor: ActorRef[Command] = ...
orderActor ! PlaceOrder("product-1", 2)
```

### Get Inventory
```scala
val inventory = FileStore.loadInventory()
// Map("product-1" -> 10, "product-2" -> 5)
```

### View Payment Records
```scala
val payments = FileStore.loadPayments()
// List(PaymentRecord(...), PaymentRecord(...), ...)
```

---

## 🛠️ Tech Stack

| Technology | Version | Purpose |
|------|------|------|
| Scala | 2.13 | Programming Language |
| Akka Typed | 2.6+ | Actor Framework |
| SBT | 1.12.5 | Build Tool |
| Java | 17.0.14 | JVM |

---

## ✅ Compilation Status

```
[success] Total time: 2 s, completed 2026-03-24 22:13:18
```

✅ **All code compiled successfully with no errors**

---

## 📚 Future Extension Recommendations

### 1. Integrate Real Payment Gateway
```scala
// Use akka-http to call third-party payment API
// Handle timeouts and retries
// Encrypt sensitive information
```

### 2. Implement HttpRequestActor
```scala
// Use akka-http to send HTTP requests
// Support various HTTP methods
// Record request/response history
```

### 3. Add Database Support
```scala
// Use Slick or Doobie for persistence
// Replace file storage
// Support transactions
```

### 4. Error Handling and Recovery
```scala
// Implement Supervisor strategies
// Handle Actor failures
// Implement dead letter queue
```

### 5. Monitoring and Logging
```scala
// Integrate Prometheus monitoring
// Implement distributed tracing
// Enhance logging system
```

---

## 📖 Thesis-Related Applications

This project can be used to verify the following:

1. **Petri Net Modeling**
   - State: order status, inventory, payment status
   - Transitions: order placement, inventory check, payment processing
   - Tokens: orders, amounts

2. **Formal Verification**
   - Deadlock freedom
   - Reachability analysis
   - Invariant verification

3. **LTL Properties**
   - Safety: orders are not processed twice
   - Liveness: orders eventually complete or fail
   - Fairness: all orders are processed

4. **Akka Features**
   - Asynchronous message passing
   - Actor isolation
   - Fault tolerance mechanisms

---

## 📂 Project Structure

```
projet_prf/
├── build.sbt                          # SBT Configuration
├── IMPLEMENTATION_SUMMARY.md          # Implementation Summary
├── README.md                          # This file
├── project/
│   └── build.properties              # SBT Version Configuration
├── src/
│   └── main/
│       └── scala/
│           ├── Protocol.scala         # Message Protocol ✨ Enhanced
│           ├── OrderActor.scala       # Order Processing ✨ Enhanced
│           ├── InventoryActor.scala   # Inventory Management
│           ├── PaymentActor.scala     # Payment Processing ✨ NEW
│           ├── HttpRequestActor.scala # HTTP Requests ✨ NEW
│           ├── FlieStore.scala        # Data Persistence ✨ Enhanced
│           └── Main.scala             # Main Entry Point ✨ Enhanced
└── target/                            # Compilation Output
    └── scala-2.13/
        └── classes/                   # Compiled Classes
```

---

## 🤝 Contributing Guidelines

Suggested improvement directions:

1. **Unit Tests** - Write ScalaTest test cases
2. **Integration Tests** - Test complete order workflows
3. **Performance Tests** - Stress test Actor throughput
4. **Documentation** - Add Scaladoc comments

---

## 📄 License

MIT License

---

## 📞 Support

For questions or suggestions, refer to:
- `IMPLEMENTATION_SUMMARY.md` - Detailed implementation summary
- Code comments - Design intentions for each Actor

---

**Last Updated**: 2026-03-24  
**Compilation Status**: ✅ Success  
**Development Environment**: Windows PowerShell, Scala 2.13, Akka Typed

