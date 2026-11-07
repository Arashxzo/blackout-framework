import {
	env,
	createExecutionContext,
	waitOnExecutionContext,
} from "cloudflare:test";
import { describe, it, expect, beforeAll, beforeEach } from "vitest";
import worker from "../src/index";

const IncomingRequest = Request<unknown, IncomingRequestCfProperties>;

// Helper to call the worker directly with a given path/method/body/headers
async function callWorker(
	path: string,
	options: {
		method?: string;
		body?: any;
		headers?: Record<string, string>;
	} = {}
) {
	const init: RequestInit = {
		method: options.method ?? "GET",
		headers: {
			"Content-Type": "application/json",
			...(options.headers ?? {}),
		},
	};
	if (options.body !== undefined) {
		init.body = JSON.stringify(options.body);
	}
	const request = new IncomingRequest(`http://example.com${path}`, init);
	const ctx = createExecutionContext();
	const response = await worker.fetch(request, env, ctx);
	await waitOnExecutionContext(ctx);
	return response;
}

function adminAuth() {
	return { Authorization: `Bearer ${env.ADMIN_SECRET}` };
}

// ─────────────────────────────────────────────────────────────
// Global setup: create all tables once, mirroring schema.sql
// ─────────────────────────────────────────────────────────────
beforeAll(async () => {
	await env.blackout_db
		.prepare(
			`CREATE TABLE IF NOT EXISTS licenses (
				device_id TEXT PRIMARY KEY,
				credits INTEGER NOT NULL DEFAULT 0,
				is_banned INTEGER DEFAULT 0,
				total_sms_sent INTEGER DEFAULT 0,
				created_at TEXT NOT NULL DEFAULT (datetime('now'))
			)`
		)
		.run();

	await env.blackout_db
		.prepare(
			`CREATE TABLE IF NOT EXISTS transactions (
				id TEXT PRIMARY KEY,
				device_id TEXT NOT NULL,
				amount_usd REAL NOT NULL,
				credits INTEGER NOT NULL,
				status TEXT NOT NULL,
				created_at TEXT NOT NULL DEFAULT (datetime('now')),
				updated_at TEXT NOT NULL DEFAULT (datetime('now'))
			)`
		)
		.run();

	await env.blackout_db
		.prepare(
			`CREATE TABLE IF NOT EXISTS plans (
				id TEXT PRIMARY KEY,
				name TEXT NOT NULL,
				price_usd REAL NOT NULL,
				credits INTEGER NOT NULL,
				is_active INTEGER DEFAULT 1
			)`
		)
		.run();

	await env.blackout_db
		.prepare(
			`INSERT OR IGNORE INTO plans (id, name, price_usd, credits) VALUES
				('starter', 'STARTER', 15.0, 25),
				('pro', 'PRO', 40.0, 100),
				('elite', 'ELITE', 100.0, 300)`
		)
		.run();
});

// Clean test data before each test (but keep tables & seed plans)
beforeEach(async () => {
	await env.blackout_db
		.prepare("DELETE FROM licenses WHERE device_id LIKE 'test-%'")
		.run();
	await env.blackout_db
		.prepare("DELETE FROM transactions WHERE device_id LIKE 'test-%'")
		.run();
});

// ─────────────────────────────────────────────────────────────
// Basic routing
// ─────────────────────────────────────────────────────────────
describe("BLACKOUT backend — basic routing", () => {
	it("returns 404 on unknown route", async () => {
		const res = await callWorker("/api/unknown");
		expect(res.status).toBe(404);
		expect(await res.text()).toBe("Not Found");
	});

	it("returns 404 on root path", async () => {
		const res = await callWorker("/");
		expect(res.status).toBe(404);
	});

	it("handles CORS preflight (OPTIONS)", async () => {
		const res = await callWorker("/api/get-plans", { method: "OPTIONS" });
		expect(res.status).toBe(200);
		expect(res.headers.get("Access-Control-Allow-Origin")).toBe("*");
		expect(res.headers.get("Access-Control-Allow-Methods")).toContain("GET");
	});

	it("returns 404 when method is wrong for a known path", async () => {
		const res = await callWorker("/api/get-plans", { method: "POST", body: {} });
		expect(res.status).toBe(404);
	});
});

// ─────────────────────────────────────────────────────────────
// /api/get-plans
// ─────────────────────────────────────────────────────────────
describe("BLACKOUT backend — /api/get-plans", () => {
	it("returns an array of active plans", async () => {
		const res = await callWorker("/api/get-plans");
		expect(res.status).toBe(200);
		expect(res.headers.get("Content-Type")).toContain("application/json");

		const plans = (await res.json()) as any[];
		expect(Array.isArray(plans)).toBe(true);
		expect(plans.length).toBeGreaterThan(0);

		const starter = plans.find((p) => p.id === "starter");
		expect(starter).toBeDefined();
		expect(starter.name).toBe("STARTER");
		expect(starter.credits).toBe(25);
		expect(starter.price_usd).toBe(15);
	});

	it("does not return inactive plans", async () => {
		await env.blackout_db
			.prepare("UPDATE plans SET is_active = 0 WHERE id = 'pro'")
			.run();

		const res = await callWorker("/api/get-plans");
		const plans = (await res.json()) as any[];
		expect(plans.some((p) => p.id === "pro")).toBe(false);

		await env.blackout_db
			.prepare("UPDATE plans SET is_active = 1 WHERE id = 'pro'")
			.run();
	});
});

// ─────────────────────────────────────────────────────────────
// /api/get-credits
// ─────────────────────────────────────────────────────────────
describe("BLACKOUT backend — /api/get-credits", () => {
	it("returns 400 if device_id is missing", async () => {
		const res = await callWorker("/api/get-credits");
		expect(res.status).toBe(400);
	});

	it("auto-creates a new user with 0 credits", async () => {
		const res = await callWorker("/api/get-credits?device_id=test-device-new");
		expect(res.status).toBe(200);
		const data = (await res.json()) as any;
		expect(data.credits).toBe(0);
		expect(data.banned).toBe(false);

		const row: any = await env.blackout_db
			.prepare("SELECT credits FROM licenses WHERE device_id = ?")
			.bind("test-device-new")
			.first();
		expect(row).toBeDefined();
		expect(row.credits).toBe(0);
	});

	it("returns existing credits for known device", async () => {
		await env.blackout_db
			.prepare(
				"INSERT INTO licenses (device_id, credits, is_banned, total_sms_sent) VALUES (?, ?, ?, ?)"
			)
			.bind("test-device-known", 42, 0, 10)
			.run();

		const res = await callWorker("/api/get-credits?device_id=test-device-known");
		const data = (await res.json()) as any;
		expect(data.credits).toBe(42);
		expect(data.banned).toBe(false);
	});

	it("returns banned:true for banned users", async () => {
		await env.blackout_db
			.prepare(
				"INSERT INTO licenses (device_id, credits, is_banned, total_sms_sent) VALUES (?, ?, ?, ?)"
			)
			.bind("test-device-banned", 100, 1, 0)
			.run();

		const res = await callWorker("/api/get-credits?device_id=test-device-banned");
		const data = (await res.json()) as any;
		expect(data.banned).toBe(true);
		expect(data.credits).toBe(0);
	});
});

// ─────────────────────────────────────────────────────────────
// /api/consume-credits
// ─────────────────────────────────────────────────────────────
describe("BLACKOUT backend — /api/consume-credits", () => {
	it("rejects invalid amount", async () => {
		const res = await callWorker("/api/consume-credits", {
			method: "POST",
			body: { deviceId: "test-consume-1", amount: -5 },
		});
		expect(res.status).toBe(400);
		const data = (await res.json()) as any;
		expect(data.success).toBe(false);
	});

	it("returns 'Not enough credits' when balance is insufficient", async () => {
		await env.blackout_db
			.prepare(
				"INSERT INTO licenses (device_id, credits, is_banned, total_sms_sent) VALUES (?, ?, ?, ?)"
			)
			.bind("test-consume-poor", 2, 0, 0)
			.run();

		const res = await callWorker("/api/consume-credits", {
			method: "POST",
			body: { deviceId: "test-consume-poor", amount: 10 },
		});
		const data = (await res.json()) as any;
		expect(data.success).toBe(false);
	});

	it("deducts credits and increments total_sms_sent on success", async () => {
		await env.blackout_db
			.prepare(
				"INSERT INTO licenses (device_id, credits, is_banned, total_sms_sent) VALUES (?, ?, ?, ?)"
			)
			.bind("test-consume-ok", 50, 0, 5)
			.run();

		const res = await callWorker("/api/consume-credits", {
			method: "POST",
			body: { deviceId: "test-consume-ok", amount: 10 },
		});
		const data = (await res.json()) as any;
		expect(data.success).toBe(true);
		expect(data.credits).toBe(40);

		const row: any = await env.blackout_db
			.prepare("SELECT credits, total_sms_sent FROM licenses WHERE device_id = ?")
			.bind("test-consume-ok")
			.first();
		expect(row.credits).toBe(40);
		expect(row.total_sms_sent).toBe(15);
	});

	it("rejects banned users", async () => {
		await env.blackout_db
			.prepare(
				"INSERT INTO licenses (device_id, credits, is_banned, total_sms_sent) VALUES (?, ?, ?, ?)"
			)
			.bind("test-consume-banned", 100, 1, 0)
			.run();

		const res = await callWorker("/api/consume-credits", {
			method: "POST",
			body: { deviceId: "test-consume-banned", amount: 5 },
		});
		const data = (await res.json()) as any;
		expect(data.success).toBe(false);
		expect(data.msg).toBe("Banned");
	});
});

// ─────────────────────────────────────────────────────────────
// /api/admin/* — auth guards
// ─────────────────────────────────────────────────────────────
describe("BLACKOUT backend — /api/admin/* (auth)", () => {
	it("blocks all admin routes without auth", async () => {
		const routes = [
			"/api/admin/stats",
			"/api/admin/users",
			"/api/admin/transactions",
			"/api/admin/plans",
		];
		for (const route of routes) {
			const res = await callWorker(route);
			expect(res.status).toBe(401);
		}
	});

	it("blocks with wrong Bearer token", async () => {
		const res = await callWorker("/api/admin/stats", {
			headers: { Authorization: "Bearer wrong-password" },
		});
		expect(res.status).toBe(401);
	});

	it("allows admin with correct token", async () => {
		const res = await callWorker("/api/admin/stats", { headers: adminAuth() });
		expect(res.status).toBe(200);
		const data = (await res.json()) as any;
		expect(data).toHaveProperty("users");
		expect(data).toHaveProperty("sms");
		expect(data).toHaveProperty("revenue");
	});

	it("admin/users returns array", async () => {
		const res = await callWorker("/api/admin/users", { headers: adminAuth() });
		expect(res.status).toBe(200);
		const data = (await res.json()) as any[];
		expect(Array.isArray(data)).toBe(true);
	});

	it("admin/transactions returns array", async () => {
		const res = await callWorker("/api/admin/transactions", {
			headers: adminAuth(),
		});
		expect(res.status).toBe(200);
		const data = (await res.json()) as any[];
		expect(Array.isArray(data)).toBe(true);
	});

	it("admin/plans returns array of all plans", async () => {
		const res = await callWorker("/api/admin/plans", { headers: adminAuth() });
		expect(res.status).toBe(200);
		const data = (await res.json()) as any[];
		expect(Array.isArray(data)).toBe(true);
		expect(data.length).toBeGreaterThanOrEqual(3);
	});
});

// ─────────────────────────────────────────────────────────────
// /api/admin/user-action
// ─────────────────────────────────────────────────────────────
describe("BLACKOUT backend — /api/admin/user-action", () => {
	it("rejects unknown action with 400", async () => {
		const res = await callWorker("/api/admin/user-action", {
			method: "POST",
			headers: adminAuth(),
			body: { deviceId: "test-admin-1", action: "hack" },
		});
		expect(res.status).toBe(400);
		const data = (await res.json()) as any;
		expect(data.success).toBe(false);
		expect(data.error).toContain("Unknown action");
	});

	it("rejects missing deviceId", async () => {
		const res = await callWorker("/api/admin/user-action", {
			method: "POST",
			headers: adminAuth(),
			body: { action: "ban" },
		});
		expect(res.status).toBe(400);
	});

	it("bans and unbans a user", async () => {
		await env.blackout_db
			.prepare("INSERT INTO licenses (device_id, credits) VALUES (?, ?)")
			.bind("test-admin-ban", 10)
			.run();

		await callWorker("/api/admin/user-action", {
			method: "POST",
			headers: adminAuth(),
			body: { deviceId: "test-admin-ban", action: "ban" },
		});
		let row: any = await env.blackout_db
			.prepare("SELECT is_banned FROM licenses WHERE device_id = ?")
			.bind("test-admin-ban")
			.first();
		expect(row.is_banned).toBe(1);

		await callWorker("/api/admin/user-action", {
			method: "POST",
			headers: adminAuth(),
			body: { deviceId: "test-admin-ban", action: "unban" },
		});
		row = await env.blackout_db
			.prepare("SELECT is_banned FROM licenses WHERE device_id = ?")
			.bind("test-admin-ban")
			.first();
		expect(row.is_banned).toBe(0);
	});

	it("sets credits for a user", async () => {
		await env.blackout_db
			.prepare("INSERT INTO licenses (device_id, credits) VALUES (?, ?)")
			.bind("test-admin-set", 0)
			.run();

		await callWorker("/api/admin/user-action", {
			method: "POST",
			headers: adminAuth(),
			body: { deviceId: "test-admin-set", action: "set_credits", value: 500 },
		});

		const row: any = await env.blackout_db
			.prepare("SELECT credits FROM licenses WHERE device_id = ?")
			.bind("test-admin-set")
			.first();
		expect(row.credits).toBe(500);
	});

	it("rejects negative credits on set_credits", async () => {
		await env.blackout_db
			.prepare("INSERT INTO licenses (device_id, credits) VALUES (?, ?)")
			.bind("test-admin-neg", 10)
			.run();

		const res = await callWorker("/api/admin/user-action", {
			method: "POST",
			headers: adminAuth(),
			body: { deviceId: "test-admin-neg", action: "set_credits", value: -50 },
		});
		expect(res.status).toBe(400);
	});

	it("deletes a user", async () => {
		await env.blackout_db
			.prepare("INSERT INTO licenses (device_id, credits) VALUES (?, ?)")
			.bind("test-admin-del", 10)
			.run();

		await callWorker("/api/admin/user-action", {
			method: "POST",
			headers: adminAuth(),
			body: { deviceId: "test-admin-del", action: "delete" },
		});

		const row: any = await env.blackout_db
			.prepare("SELECT * FROM licenses WHERE device_id = ?")
			.bind("test-admin-del")
			.first();
		expect(row).toBeNull();
	});
});

// ─────────────────────────────────────────────────────────────
// /api/nowpayments-webhook
// ─────────────────────────────────────────────────────────────
describe("BLACKOUT backend — /api/nowpayments-webhook", () => {
	it("rejects requests without signature header", async () => {
		const res = await callWorker("/api/nowpayments-webhook", {
			method: "POST",
			body: { invoice_id: "abc", payment_status: "finished" },
		});
		expect(res.status).toBe(401);
	});

	it("rejects requests with invalid signature", async () => {
		const res = await callWorker("/api/nowpayments-webhook", {
			method: "POST",
			headers: { "x-nowpayments-sig": "deadbeef" },
			body: { invoice_id: "abc", payment_status: "finished" },
		});
		expect(res.status).toBe(401);
	});

	it("rejects requests with invalid JSON body", async () => {
		const request = new IncomingRequest(
			"http://example.com/api/nowpayments-webhook",
			{
				method: "POST",
				headers: { "x-nowpayments-sig": "deadbeef" },
				body: "not-json",
			}
		);
		const ctx = createExecutionContext();
		const res = await worker.fetch(request, env, ctx);
		await waitOnExecutionContext(ctx);
		expect(res.status).toBe(400);
	});
});