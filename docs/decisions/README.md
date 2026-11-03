# Decision records

One record per non-trivial choice, numbered `NNN-title.md`. Each follows the same shape:

```
# NNN — <decision>
Context: what forced a choice
Options considered: A / B / C
Decision:
Why:
What I gave up:
Revisit if:
```

Superseded records (002, 003, 004 — the Kafka design) are kept and point to the record
that replaced them (012).
