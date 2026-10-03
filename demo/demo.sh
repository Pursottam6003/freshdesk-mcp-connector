#!/usr/bin/env bash
set -euo pipefail

BASE_URL="http://localhost:8080"
MCP_URL="${BASE_URL}/mcp"
ADMIN_URL="${BASE_URL}/mock-admin/faults"

format_json() {
  if command -v jq >/dev/null 2>&1; then
    jq .
  elif command -v python >/dev/null 2>&1; then
    python -m json.tool
  else
    cat
  fi
}

echo "=================================================================="
echo " Freshdesk Connector MCP Demo (9 Beats Walkthrough)"
echo "=================================================================="

echo -e "\n[Beat 1] Protocol Handshake: initialize"
curl -s -X POST "${MCP_URL}" -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {}
}' | format_json

echo -e "\n[Beat 2] Protocol Notification: notifications/initialized (Expect HTTP 202)"
curl -s -i -X POST "${MCP_URL}" -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0",
  "method": "notifications/initialized"
}' | head -n 5

echo -e "\n[Beat 3] Tool Discovery: tools/list"
curl -s -X POST "${MCP_URL}" -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list"
}' | format_json

echo -e "\n[Beat 4] Discovery with Limits: search_tickets for payment failures"
curl -s -X POST "${MCP_URL}" -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "search_tickets",
    "arguments": {
      "query": "payment",
      "status": "open",
      "limit": 2
    }
  }
}' | format_json

echo -e "\n[Beat 5] Single Ticket Inspection: get_ticket"
curl -s -X POST "${MCP_URL}" -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "tools/call",
  "params": {
    "name": "get_ticket",
    "arguments": {
      "ticket_id": 1
    }
  }
}' | format_json

echo -e "\n[Beat 6] Conversation Thread & Private Note Drop (Default)"
curl -s -X POST "${MCP_URL}" -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0",
  "id": 5,
  "method": "tools/call",
  "params": {
    "name": "get_ticket_conversations",
    "arguments": {
      "ticket_id": 1
    }
  }
}' | format_json

echo -e "\n[Beat 7] Security Policy Gate: Opt-in to private notes"
curl -s -X POST "${MCP_URL}" -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0",
  "id": 6,
  "method": "tools/call",
  "params": {
    "name": "get_ticket_conversations",
    "arguments": {
      "ticket_id": 1,
      "include_private_notes": true
    }
  }
}' | format_json

echo -e "\n[Beat 8] Rate Limiting & Retry Walkthrough (Arm 1x 429 fault)"
curl -s -X POST "${ADMIN_URL}" -H "Content-Type: application/json" -d '{"arm_429": 1, "retry_after": 2}'
echo "Fault armed. Calling get_ticket (Retrying after 2s)..."
curl -s -X POST "${MCP_URL}" -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0",
  "id": 7,
  "method": "tools/call",
  "params": {
    "name": "get_ticket",
    "arguments": {
      "ticket_id": 2
    }
  }
}' | format_json

echo -e "\n[Beat 9] Deterministic Pagination & 5-Page/500-Ticket Budget: list_tickets"
curl -s -X POST "${MCP_URL}" -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0",
  "id": 8,
  "method": "tools/call",
  "params": {
    "name": "list_tickets",
    "arguments": {
      "status": "open",
      "limit": 5
    }
  }
}' | format_json

# Reset faults
curl -s -X POST "${ADMIN_URL}" -H "Content-Type: application/json" -d '{"reset": true}' > /dev/null
echo -e "\nDemo completed successfully."