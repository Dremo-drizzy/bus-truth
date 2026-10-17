# infra — PostgreSQL for development, and the whole stack for production

| file | for |
|---|---|
| `docker-compose.yml` | local development: PostgreSQL only, **published on 55432** |
| `docker-compose.prod.yml` | a host: PostgreSQL, collector, api, site, nightly dump — **one published port, 80** |
| `collector.Dockerfile`, `api.Dockerfile`, `web.Dockerfile` | the three images, built from the repository root |
| `nginx.conf` | serves the built site, proxies `/api` so the frontend stays same-origin |
| `backup.sh` | the nightly `pg_dump`, gzipped, kept a fortnight |

Kafka and its UI are gone, with the streaming version — see
[ADR-012](../docs/decisions/012-replace-streaming-with-batch.md).

**Two files, not one, and that is the point.** Development needs the database reachable
from the host, because `psql`, the processor and the backfill all run there. Production
needs it unreachable. That difference is a topology, not a value, so it is expressed as
the *absence* of a `ports:` key rather than as a variable someone can forget to set:

```
$ docker compose -f infra/docker-compose.prod.yml config | grep published
web: published: "80"
```

The previous version of this project published 5432 and an unauthenticated Kafka UI on
8080. See [ADR-016](../docs/decisions/016-how-it-is-deployed.md) and
[docs/DEPLOY.md](../docs/DEPLOY.md).

```bash
# development
docker compose -f infra/docker-compose.yml up -d --wait

# a host, once .env exists beside the clone
docker compose -f infra/docker-compose.prod.yml up -d --build
```
