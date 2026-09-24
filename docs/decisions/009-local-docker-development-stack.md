# 009 — Local development stack in Docker Compose

Context: the project needs PostgreSQL and Kafka to develop against. Those have to exist
somewhere before the collector, loader or processor can do anything real, and whatever runs
locally should behave like what will eventually run on a server.

Options considered:
A. Install PostgreSQL and Kafka natively on the laptop.
B. Docker Compose: one file describing every piece of infrastructure.
C. A shared cloud development instance.

Decision: B. `infra/docker-compose.yml` defines three services — `postgres:18-alpine`,
`apache/kafka:4.3.1` in KRaft mode, and `kafbat/kafka-ui`. Credentials come from a
git-ignored `.env`; `.env.example` is the committed template.

Why:
- The same real software and the same versions as production, rather than an approximation.
- One command instead of installing databases by hand, and anyone who clones the repository
  gets an identical setup with no written instructions to follow.
- It can be wiped and recreated in seconds, so a broken database is never a lost afternoon.
- Native installs (A) drift: the versions are whatever the installer gave that laptop, and
  uninstalling them cleanly is its own project. A shared cloud instance (C) costs money,
  needs internet, and lets one person's experiment break everyone else's work.

Three choices inside that decision:
- **KRaft, not ZooKeeper.** Kafka 4.x removed ZooKeeper entirely; brokers run their own
  Raft-based controller quorum. One container is both broker and controller here. Using
  ZooKeeper would mean learning and running a component that no longer exists upstream.
- **kafbat, not provectus, for the UI.** The provectus project is archived; kafbat is the
  maintained fork.
- **Two advertised listeners, not one.** A Kafka client connects twice: once to bootstrap,
  then again to whatever address the broker *advertises* back. That address must be
  reachable from wherever the client is running, and there are two different "wheres" here:
  containers on the Docker network reach the broker at `kafka:9092`, while the Java services
  running on my laptop reach it at `localhost:29092`. One listener would break one of them —
  a container cannot resolve `localhost` as the broker, and the host cannot resolve `kafka`.

What I gave up:
- Docker Desktop has to be installed and running, and the images are a few gigabytes.
- Containers are not identical to managed cloud services. A managed Kafka or a managed
  Postgres has its own configuration, limits and failure modes that this stack cannot show.
- A single broker with replication factor 1 cannot exhibit replication or failover problems,
  so a class of production issues is invisible locally.
- Some performance realism: everything shares one laptop's disk and CPU.

Revisit if:
- **Deploying to a public server.** This file publishes PostgreSQL on 5432 and an
  unauthenticated kafka-ui on 8080. That is correct for local development and dangerous on
  a public VM: in Stage 5 those ports must not be exposed to the internet, and kafka-ui
  needs authentication or no public route at all.
- Production uses managed Kafka or Postgres, in which case the configuration here should be
  checked against what the managed service actually does.
- More brokers are needed to test replication behaviour realistically.

Note for future me: a volume must be mounted at the exact path the image declares as its
volume. Mounting `/var/lib/kafka` instead of `/var/lib/kafka/data` left the named volume
empty while Docker quietly created a throwaway anonymous volume for the real data path, and
every restart began from nothing. A healthy container proves nothing about persistence; only
a `down`/`up` cycle with a topic and a row written first does.
