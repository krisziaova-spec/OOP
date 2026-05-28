#!/usr/bin/env bash
set -e

export DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://localhost:5432/pdts_db}"
export DATABASE_USERNAME="${DATABASE_USERNAME:-pdts_user}"
export DATABASE_PASSWORD="${DATABASE_PASSWORD:-Pdts@123}"
export PORT="${PORT:-8080}"

export RESEND_API_KEY="${RESEND_API_KEY:-dummy}"
export RESEND_FROM_EMAIL="${RESEND_FROM_EMAIL:-PDTS Registrar <onboarding@resend.dev>}"
export APP_BASE_URL="${APP_BASE_URL:-http://localhost:8080}"

mkdir -p uploads/requirements

mvn clean spring-boot:run
