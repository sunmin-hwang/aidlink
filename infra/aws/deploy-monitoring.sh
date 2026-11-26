#!/usr/bin/env bash
set -euo pipefail
cd "${AIDLINK_DEPLOY_ROOT:-/opt/aidlink}"
# Monitoring is opt-in, independently managed from application rollback.
if [[ ! -f .env.monitoring ]]; then
  echo 'Monitoring skipped: .env.monitoring is not configured.'
  exit 0
fi
umask 077
exec 9>.monitoring-deploy.lock
flock -w 300 9
# Resolve JSON log paths again after app replacement; keep the generated file private.
targets=$(mktemp .env.monitoring-targets.XXXXXX)
trap 'rm -f "$targets"' EXIT
for service in backend frontend caddy; do
  log_path=$(docker inspect -f '{{.LogPath}}' "aidlink-aws-${service}-1")
  [[ "$log_path" == /var/lib/docker/containers/*/*-json.log ]] || {
    echo "Expected a Docker JSON log path for $service" >&2
    exit 1
  }
  printf '%s_LOG_PATH=%s\n' "${service^^}" "$log_path" >>"$targets"
done
mv "$targets" .env.monitoring-targets
compose() { docker compose --env-file .env.monitoring -f docker-compose.aws-monitoring.yml "$@"; }
compose config --quiet
config_hash=$({
  sha256sum docker-compose.aws-monitoring.yml
  find infra/monitoring/aws -type f -print0 | sort -z | xargs -0 sha256sum
} | sha256sum | cut -d ' ' -f 1)
# Bind-mounted configuration changes do not themselves trigger Compose recreation.
if [[ ! -f .monitoring-config.sha256 ]] || [[ "$(cat .monitoring-config.sha256)" != "$config_hash" ]]; then
  compose up -d --no-build --force-recreate
else
  compose up -d --no-build
fi
for attempt in $(seq 1 45); do
  if curl --max-time 3 -fsS http://127.0.0.1:19090/-/ready >/dev/null \
    && curl --max-time 3 -fsS http://127.0.0.1:13002/api/health >/dev/null \
    && curl --max-time 3 -fsS http://127.0.0.1:13100/ready >/dev/null \
    && [[ "$(docker inspect -f '{{.State.Running}}' aidlink-monitoring-alloy-1)" == true ]]; then
    printf '%s\n' "$config_hash" >.monitoring-config.sha256
    echo 'Monitoring ready. Grafana is available only through SSH forwarding.'
    exit 0
  fi
  sleep 4
done
echo 'Monitoring readiness failed. Inspect monitoring logs; application images were not rolled back.' >&2
exit 1
