#!/usr/bin/env bash
set -euo pipefail
umask 077
cd "${AIDLINK_DEPLOY_ROOT:-/opt/aidlink}"
exec 9>.deploy.lock
flock -w 900 9

backend="${1:?backend image required}"
frontend="${2:?frontend image required}"
for image in "$backend" "$frontend"; do
  [[ "$image" =~ ^[a-z0-9./:_@-]+$ ]] || { echo 'Invalid image reference' >&2; exit 1; }
  docker image inspect "$image" >/dev/null 2>&1 || docker pull "$image"
done

compose() { docker compose --env-file .env.aws --env-file .env.images -f docker-compose.aws-lean.yml "$@"; }
check_ready() {
  for attempt in $(seq 1 90); do
    if curl --max-time 3 -fsS http://127.0.0.1:18080/actuator/health/readiness >/dev/null \
      && curl --max-time 3 -fsS http://127.0.0.1:13000/auth >/dev/null; then
      return 0
    fi
    sleep 4
  done
  return 1
}

had_previous=false
if [[ -f .env.images ]]; then
  cp .env.images .env.images.previous
  had_previous=true
fi
printf 'AIDLINK_BACKEND_IMAGE=%s\nAIDLINK_FRONTEND_IMAGE=%s\n' "$backend" "$frontend" >.env.images
compose config --quiet
if ! compose up -d --no-build || ! check_ready; then
  echo 'Application readiness failed. Restoring previous images when available.' >&2
  if [[ "$had_previous" == true ]]; then
    cp .env.images.previous .env.images
    compose up -d --no-build
    check_ready || echo 'Rollback readiness also failed; inspect server logs.' >&2
  fi
  # Database migrations are not undone by an image rollback.
  exit 1
fi
compose ps
echo 'Deployment passed backend readiness and frontend HTTP checks.'
# Keep rollback images. Remove only unused layers, without touching volumes.
docker image prune -f --filter 'until=168h'
