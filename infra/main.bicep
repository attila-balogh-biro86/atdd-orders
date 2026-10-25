// Minimal platform for the reference project: ACR + Log Analytics + Container Apps environment.
// Container apps themselves (orders-staging, orders-prod, orders-pr-N) are created by the pipeline
// (ci/deploy-containerapp.sh), so environments stay cheap and disposable.
//
//   az group create -n rg-orders-atdd -l westeurope
//   az deployment group create -g rg-orders-atdd -f infra/main.bicep -p prefix=ordersatdd

@description('Short lowercase prefix for resource names')
param prefix string
param location string = resourceGroup().location

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: '${prefix}acr'
  location: location
  sku: { name: 'Basic' }
  properties: { adminUserEnabled: false }
}

resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: '${prefix}-logs'
  location: location
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
  }
}

resource env 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: '${prefix}-env'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
  }
}

output acrName string = acr.name
output containerAppsEnvironment string = env.name
