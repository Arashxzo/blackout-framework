-- ============================================================
-- BLACKOUT — Cloudflare D1 Schema
-- Run with:
--   npx wrangler d1 execute blackout-db --file=./schema.sql --remote
-- ============================================================

-- Create licenses table
CREATE TABLE IF NOT EXISTS licenses (
  device_id TEXT PRIMARY KEY,
  credits INTEGER NOT NULL DEFAULT 0,
  is_banned INTEGER DEFAULT 0,
  total_sms_sent INTEGER DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Create transactions table
CREATE TABLE IF NOT EXISTS transactions (
  id TEXT PRIMARY KEY,
  device_id TEXT NOT NULL,
  amount_usd REAL NOT NULL,
  credits INTEGER NOT NULL,
  status TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Create plans table
CREATE TABLE IF NOT EXISTS plans (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  price_usd REAL NOT NULL,
  credits INTEGER NOT NULL,
  is_active INTEGER DEFAULT 1
);

-- Insert default plans (INSERT OR IGNORE = safe to re-run)
INSERT OR IGNORE INTO plans (id, name, price_usd, credits) VALUES 
  ('starter', 'STARTER', 15.0, 25),
  ('pro', 'PRO', 40.0, 100),
  ('elite', 'ELITE', 100.0, 300);