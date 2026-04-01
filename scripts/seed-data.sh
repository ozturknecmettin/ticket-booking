#!/usr/bin/env bash
# =============================================================================
# seed-data.sh — Populate demo data across all ticket-booking services
#
# Usage:
#   ./scripts/seed-data.sh                    # all services on localhost
#   SHOW_URL=http://host:8081 ./scripts/seed-data.sh  # override base URLs
#
# What it seeds:
#   Shows      : 3 active shows (if not already present via DevDataSeeder)
#   Bookings   : 4 bookings covering all saga paths
#     booking-1 (show-001, seat A1) → payment CONFIRMED → TICKET_ISSUED
#     booking-2 (show-001, seat A2) → payment CONFIRMED → TICKET_ISSUED
#     booking-3 (show-002, seat B1) → payment left as REQUESTED (pending demo)
#     booking-4 (show-002, seat B2) → payment FAILED → booking CANCELLED
# =============================================================================

set -euo pipefail

SHOW_URL="${SHOW_URL:-http://localhost:8081}"
BOOKING_URL="${BOOKING_URL:-http://localhost:8082}"
PAYMENT_URL="${PAYMENT_URL:-http://localhost:8083}"
NOTIFICATION_URL="${NOTIFICATION_URL:-http://localhost:8084}"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

info()    { echo -e "${CYAN}[seed]${NC} $*"; }
success() { echo -e "${GREEN}[ok]${NC}   $*"; }
warn()    { echo -e "${YELLOW}[warn]${NC} $*"; }
error()   { echo -e "${RED}[err]${NC}  $*"; exit 1; }

# ── helpers ──────────────────────────────────────────────────────────────────

wait_healthy() {
    local name=$1 url=$2 retries=20
    info "Waiting for $name at $url/actuator/health ..."
    for i in $(seq 1 $retries); do
        if curl -sf "$url/actuator/health" | grep -q '"UP"'; then
            success "$name is up"
            return
        fi
        sleep 3
    done
    error "$name did not become healthy after $((retries * 3))s"
}

post() {
    local url=$1 body=$2
    curl -sf -X POST "$url" \
        -H "Content-Type: application/json" \
        -d "$body"
}

get() {
    curl -sf "$1"
}

# ── wait for all services ─────────────────────────────────────────────────────

echo ""
info "============================================================"
info " Ticket Booking — Demo Data Seeder"
info "============================================================"
echo ""

wait_healthy "show-service"         "$SHOW_URL"
wait_healthy "booking-service"      "$BOOKING_URL"
wait_healthy "payment-service"      "$PAYMENT_URL"
wait_healthy "notification-service" "$NOTIFICATION_URL"
echo ""

# ── 1. shows ─────────────────────────────────────────────────────────────────

info "--- Step 1: Shows ---"

existing=$(get "$SHOW_URL/api/shows" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d))" 2>/dev/null || echo "0")

if [ "$existing" -ge 3 ]; then
    warn "Shows already seeded ($existing found) — skipping show creation"
    SHOW1_ID="show-001"
    SHOW2_ID="show-002"
    SHOW3_ID="show-003"
else
    SHOW1_ID=$(post "$SHOW_URL/api/shows" \
        '{"title":"Hamilton","venue":"Broadway Theater, New York","totalSeats":50,"ticketPrice":75.00}')
    success "Created show: Hamilton  (id=$SHOW1_ID)"

    SHOW2_ID=$(post "$SHOW_URL/api/shows" \
        '{"title":"The Lion King","venue":"Lyceum Theatre, London","totalSeats":30,"ticketPrice":95.00}')
    success "Created show: The Lion King  (id=$SHOW2_ID)"

    SHOW3_ID=$(post "$SHOW_URL/api/shows" \
        '{"title":"Phantom of the Opera","venue":"Her Majestys Theatre, London","totalSeats":40,"ticketPrice":85.00}')
    success "Created show: Phantom of the Opera  (id=$SHOW3_ID)"
fi

echo ""

# ── 2. bookings ───────────────────────────────────────────────────────────────

info "--- Step 2: Bookings ---"

BOOKING1_ID=$(post "$BOOKING_URL/api/bookings" \
    "{\"showId\":\"$SHOW1_ID\",\"seatNumber\":\"A1\",\"customerId\":\"customer-alice\",\"amount\":75.00}")
success "Initiated booking-1 (show-001, A1, alice) → id=$BOOKING1_ID"

BOOKING2_ID=$(post "$BOOKING_URL/api/bookings" \
    "{\"showId\":\"$SHOW1_ID\",\"seatNumber\":\"A2\",\"customerId\":\"customer-bob\",\"amount\":75.00}")
success "Initiated booking-2 (show-001, A2, bob)   → id=$BOOKING2_ID"

BOOKING3_ID=$(post "$BOOKING_URL/api/bookings" \
    "{\"showId\":\"$SHOW2_ID\",\"seatNumber\":\"B1\",\"customerId\":\"customer-alice\",\"amount\":95.00}")
success "Initiated booking-3 (show-002, B1, alice) → id=$BOOKING3_ID  [will stay PENDING]"

BOOKING4_ID=$(post "$BOOKING_URL/api/bookings" \
    "{\"showId\":\"$SHOW2_ID\",\"seatNumber\":\"B2\",\"customerId\":\"customer-charlie\",\"amount\":95.00}")
success "Initiated booking-4 (show-002, B2, charlie) → id=$BOOKING4_ID  [will be FAILED]"

echo ""
info "Waiting 3s for sagas to process payment requests..."
sleep 3

# ── 3. confirm / fail payments ────────────────────────────────────────────────

info "--- Step 3: Payment actions ---"

get_payment_id() {
    local booking_id=$1
    local retries=5
    for i in $(seq 1 $retries); do
        pid=$(get "$PAYMENT_URL/api/payments/booking/$booking_id" \
            | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['paymentId'])" 2>/dev/null || echo "")
        if [ -n "$pid" ]; then
            echo "$pid"
            return
        fi
        sleep 2
    done
    warn "Could not resolve paymentId for booking $booking_id after ${retries} retries"
    echo ""
}

PAY1_ID=$(get_payment_id "$BOOKING1_ID")
if [ -n "$PAY1_ID" ]; then
    post "$PAYMENT_URL/api/payments/$PAY1_ID/confirm" '{}' > /dev/null || true
    success "Confirmed payment for booking-1 (paymentId=$PAY1_ID)"
else
    warn "Skipped confirm for booking-1 — payment not found yet"
fi

PAY2_ID=$(get_payment_id "$BOOKING2_ID")
if [ -n "$PAY2_ID" ]; then
    post "$PAYMENT_URL/api/payments/$PAY2_ID/confirm" '{}' > /dev/null || true
    success "Confirmed payment for booking-2 (paymentId=$PAY2_ID)"
else
    warn "Skipped confirm for booking-2 — payment not found yet"
fi

PAY4_ID=$(get_payment_id "$BOOKING4_ID")
if [ -n "$PAY4_ID" ]; then
    curl -sf -X POST "$PAYMENT_URL/api/payments/$PAY4_ID/fail?reason=Insufficient+funds" > /dev/null || true
    success "Failed payment for booking-4 (paymentId=$PAY4_ID)"
else
    warn "Skipped fail for booking-4 — payment not found yet"
fi

echo ""
info "Waiting 3s for sagas to complete..."
sleep 3

# ── 4. summary ────────────────────────────────────────────────────────────────

info "--- Final State ---"
echo ""

print_booking() {
    local label=$1 id=$2
    local state
    state=$(get "$BOOKING_URL/api/bookings/$id" \
        | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status','?'), '|', d.get('ticketNumber') or '-')" 2>/dev/null || echo "unknown")
    echo -e "  ${CYAN}$label${NC}  id=$id  →  $state"
}

echo "Bookings:"
print_booking "booking-1 (alice  / show-001 / A1)" "$BOOKING1_ID"
print_booking "booking-2 (bob    / show-001 / A2)" "$BOOKING2_ID"
print_booking "booking-3 (alice  / show-002 / B1)" "$BOOKING3_ID"
print_booking "booking-4 (charlie/ show-002 / B2)" "$BOOKING4_ID"

echo ""
echo "Shows:"
get "$SHOW_URL/api/shows" \
    | python3 -c "
import sys, json
for s in json.load(sys.stdin):
    print(f\"  {s['showId']}  {s['title']:<30} seats={s['availableSeats']}/{s['totalSeats']}  status={s['status']}\")
" 2>/dev/null || warn "Could not fetch show summary"

echo ""
echo "Swagger UIs:"
echo "  show-service         → $SHOW_URL/swagger-ui.html"
echo "  booking-service      → $BOOKING_URL/swagger-ui.html"
echo "  payment-service      → $PAYMENT_URL/swagger-ui.html"
echo "  notification-service → $NOTIFICATION_URL/swagger-ui.html"
echo ""
success "Seeding complete."
