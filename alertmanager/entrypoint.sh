#!/bin/sh
set -e

touch ./discord_webhook_url
echo "${DISCORD_ALERTMANAGER_WEBHOOK_URL}" >> /etc/alertmanager/discord_webhook_url

# Start Alertmanager
exec /bin/alertmanager --config.file=/etc/alertmanager/config.yml --storage.path=/etc/alertmanager