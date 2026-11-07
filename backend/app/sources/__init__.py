"""Job-board fetchers. Each module exposes `fetch(client, slug, company) -> list[Job]`."""
from . import ashby, greenhouse, lever

FETCHERS = {
    "greenhouse": greenhouse.fetch,
    "lever": lever.fetch,
    "ashby": ashby.fetch,
}
