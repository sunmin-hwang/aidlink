#!/usr/bin/env bash
set -euo pipefail

SWAP_SIZE_GB="${SWAP_SIZE_GB:-2}"
DEPLOY_USER="${DEPLOY_USER:-ubuntu}"
DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/aidlink}"
LOKI_DRIVER_VERSION="${LOKI_DRIVER_VERSION:-3.7.0}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run this script as root." >&2
  exit 1
fi

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y docker.io docker-compose-v2 git curl ca-certificates

systemctl enable --now docker
usermod -aG docker "${DEPLOY_USER}"

if [[ ! -f /swapfile ]]; then
  fallocate -l "${SWAP_SIZE_GB}G" /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
fi

if ! swapon --show=NAME --noheadings | grep -qx /swapfile; then
  swapon /swapfile
fi

if ! grep -qF '/swapfile none swap sw 0 0' /etc/fstab; then
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

cat >/etc/sysctl.d/99-aidlink.conf <<'SYSCTL'
vm.swappiness=20
vm.vfs_cache_pressure=100
SYSCTL
sysctl --system >/dev/null

architecture="$(dpkg --print-architecture)"
case "${architecture}" in
  arm64|amd64) ;;
  *)
    echo "Unsupported architecture for the Loki Docker driver: ${architecture}" >&2
    exit 1
    ;;
esac

if ! docker plugin inspect loki >/dev/null 2>&1; then
  docker plugin install "grafana/loki-docker-driver:${LOKI_DRIVER_VERSION}-${architecture}" \
    --alias loki \
    --grant-all-permissions
fi

if [[ "$(docker plugin inspect -f '{{.Enabled}}' loki)" != "true" ]]; then
  docker plugin enable loki
fi

install -d -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "${DEPLOY_ROOT}"

echo "EC2 bootstrap complete. Log out and back in before running Docker as ${DEPLOY_USER}."
