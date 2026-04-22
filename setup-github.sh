#!/bin/bash

# UWB-BLE Gateway — GitHub Setup Script (Safe, No Token Required)
# ✅ Run this in UwbBleGatewayApp/ directory

set -e

echo "🚀 GitHub Repo Setup for UWB-BLE Gateway"
echo "========================================"

echo "1. Checking GitHub CLI (gh)..."
if ! command -v gh &> /dev/null; then
  echo "❌ 'gh' not found. Using web-based setup."
  echo "   👉 Open this link to create repo manually:"
  echo "   https://github.com/new?&repository[name]=uwb-ble-gateway-android&repository[description]=BLE+UWB+4G+Android+app+for+Xiaomi+14+Pro&repository[private]=true"
  echo "   Then run: git remote add origin <YOUR_REPO_URL> && git push -u origin main"
  exit 0
fi

echo "✅ 'gh' detected. Authenticating..."
gh auth status > /dev/null 2>&1 || { echo "⚠️  Please login first: gh auth login"; exit 1; }

echo "2. Creating private GitHub repository..."
REPO_URL=$(gh repo create uwb-ble-gateway-android \
  --private \
  --description="BLE+UWB+4G Android app for Xiaomi 14 Pro" \
  --source=. \
  --remote=origin \
  --push 2>/dev/null | grep "https")

echo "✅ Repository created!"
echo "🔗 GitHub URL: $REPO_URL"
echo "🌐 Vercel Import: https://vercel.com/new/git/external?repositoryUrl=$REPO_URL"
echo ""
echo "🎉 Done! You can now deploy to Vercel in 1 click."
echo "💡 Tip: In Vercel, import from GitHub → select 'uwb-ble-gateway-android' → deploy."