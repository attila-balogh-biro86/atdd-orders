#!/usr/bin/env bash
# Create-or-update an Azure Container App and wait until it reports healthy.
#   ci/deploy-containerapp.sh <app-name> <image> <resource-group> <containerapps-env> <acr-name>
# Writes url=https://... to $GITHUB_OUTPUT.
# max-replicas=1 because the reference app stores data in memory; raise it once you add a database.
set -euo pipefail
APP="$1"; IMAGE="$2"; RG="$3"; ENV_NAME="$4"; ACR="$5"

if az containerapp show -n "$APP" -g "$RG" >/dev/null 2>&1; then
  echo "Updating $APP → $IMAGE"
  az containerapp update -n "$APP" -g "$RG" --image "$IMAGE" \
    --set-env-vars "APP_VERSION=${GITHUB_SHA:-local}" >/dev/null
else
  echo "Creating $APP → $IMAGE"
  az containerapp create -n "$APP" -g "$RG" --environment "$ENV_NAME" \
    --image "$IMAGE" --registry-server "$ACR.azurecr.io" --registry-identity system \
    --ingress external --target-port 8080 \
    --min-replicas 1 --max-replicas 1 --cpu 0.5 --memory 1Gi \
    --env-vars "APP_VERSION=${GITHUB_SHA:-local}" >/dev/null
fi

FQDN=$(az containerapp show -n "$APP" -g "$RG" --query properties.configuration.ingress.fqdn -o tsv)
URL="https://$FQDN"

echo "Waiting for $URL/actuator/health/readiness ..."
for i in $(seq 1 40); do
  if curl -fsS "$URL/actuator/health/readiness" | grep -q '"UP"'; then
    echo "Healthy after $((i * 5))s"
    [[ -n "${GITHUB_OUTPUT:-}" ]] && echo "url=$URL" >> "$GITHUB_OUTPUT"
    exit 0
  fi
  sleep 5
done
echo "::error::$APP did not become healthy"; exit 1
