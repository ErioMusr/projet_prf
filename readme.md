# projet_prf

Order Management System + Petri Net Analyzer (Scala + Akka Typed).

## Run

```bash
sbt test
sbt "runMain Main"
sbt "runMain petrinet.VerificationMain"
```

## Current Petri Net Model (Aligned with Structural Diagram)

1. Places: P1~P6
2. Transitions: T1~T9
3. Initial marking: M(1,0,0,0,0,0)

## Notes

1. The comparative simulator validates Akka logs per orderId with independent replay.
2. Remaining Invalid firings usually indicate semantic mismatch in logs (for example, Payment_Timeout appearing before payment stage), not necessarily core business flow bugs.