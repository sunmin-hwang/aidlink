#!/usr/bin/env bash
set -euo pipefail

SWAP_SIZE_GB="${SWAP_SIZE_GB:-2}"
DEPLOY_USER="${DEPLOY_USER:-ubuntu}"
DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/aidlink}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run this script as root." >&2
  exit 1
fi

# The Elastic IP is associated just after instance creation.
for attempt in $(seq 1 60); do
  if curl --connect-timeout 5 --max-time 10 -fsS https://archive.ubuntu.com/ubuntu/ >/dev/null; then
    break
  fi
  sleep 5
done
apt-get -o Acquire::Retries=10 update
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

install -d -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "${DEPLOY_ROOT}"

# Keep application data/secrets readable only by the deployment user.
chmod 750 "${DEPLOY_ROOT}"

echo "EC2 bootstrap complete. Log out and back in before running Docker as ${DEPLOY_USER}."
