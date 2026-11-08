export interface Env {
  blackout_db: D1Database;
  NOWPAYMENTS_API_KEY: string;
  NOWPAYMENTS_IPN_SECRET: string;
  ADMIN_SECRET: string;
  SUBSCRIPTION_ENABLED?: string;
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    };

    if (request.method === 'OPTIONS') return new Response(null, { headers: corsHeaders });

    // Subscription is OPTIONAL and DISABLED by default.
    // Enable by setting SUBSCRIPTION_ENABLED=true in wrangler vars.
    const subscriptionEnabled = env.SUBSCRIPTION_ENABLED === 'true';

    const authHeader = request.headers.get('Authorization');
    const isAdmin =
      typeof env.ADMIN_SECRET === "string" &&
      env.ADMIN_SECRET.length > 0 &&
      authHeader === `Bearer ${env.ADMIN_SECRET}`;

    // ==========================================
    // 1. Get plans list
    // ==========================================
    if (url.pathname === '/api/get-plans' && request.method === 'GET') {
      const results = await env.blackout_db
        .prepare('SELECT id, name, price_usd, credits FROM plans WHERE is_active = 1')
        .all();
      return new Response(JSON.stringify(results.results), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    // ==========================================
    // 2. Create payment link
    // ==========================================
    if (url.pathname === '/api/create-payment' && request.method === 'POST') {
      if (!subscriptionEnabled) {
        return new Response(
          JSON.stringify({ error: 'Subscription system is disabled' }),
          { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      try {
        const { deviceId, planId } = (await request.json()) as {
          deviceId: string;
          planId: string;
        };

        if (!deviceId || !planId) {
          return new Response(JSON.stringify({ error: 'Missing deviceId or planId' }), {
            status: 400,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          });
        }

        const plan: any = await env.blackout_db
          .prepare('SELECT * FROM plans WHERE id = ? AND is_active = 1')
          .bind(planId)
          .first();

        if (!plan) {
          return new Response(JSON.stringify({ error: 'Invalid plan' }), {
            status: 400,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          });
        }

        const response = await fetch('https://api.nowpayments.io/v1/invoice', {
          method: 'POST',
          headers: {
            'x-api-key': env.NOWPAYMENTS_API_KEY,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            price_amount: plan.price_usd,
            price_currency: 'usd',
            pay_currency: 'usdttrc20',
            order_id: deviceId,
            order_description: `${plan.name} - ${plan.credits} SMS credits`,
            ipn_callback_url: `https://${url.hostname}/api/nowpayments-webhook`,
          }),
        });

        if (!response.ok) {
          const errText = await response.text();
          console.error('NOWPayments invoice error:', response.status, errText);
          return new Response(
            JSON.stringify({ error: 'Payment provider error', details: errText }),
            { status: 502, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
          );
        }

        const data: any = await response.json();

        if (data.id) {
          await env.blackout_db
            .prepare(
              'INSERT OR REPLACE INTO transactions (id, device_id, amount_usd, credits, status) VALUES (?, ?, ?, ?, ?)'
            )
            .bind(data.id.toString(), deviceId, plan.price_usd, plan.credits, 'pending')
            .run();
        }

        return new Response(JSON.stringify(data), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      } catch (e) {
        console.error('create-payment error:', e);
        return new Response(JSON.stringify({ error: 'Invalid request' }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }
    }

    // ==========================================
    // 3. Check credits
    // ==========================================
    if (url.pathname === '/api/get-credits' && request.method === 'GET') {
      const deviceId = url.searchParams.get('device_id');
      if (!deviceId) return new Response('Missing device_id', { status: 400, headers: corsHeaders });

      // Subscription disabled — tell client it's unlimited.
      if (!subscriptionEnabled) {
        return new Response(
          JSON.stringify({ credits: -1, banned: false, subscription_enabled: false }),
          { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      const result: any = await env.blackout_db
        .prepare('SELECT credits, is_banned FROM licenses WHERE device_id = ?')
        .bind(deviceId)
        .first();

      if (!result) {
        await env.blackout_db
          .prepare('INSERT OR IGNORE INTO licenses (device_id, credits, is_banned, total_sms_sent) VALUES (?, 0, 0, 0)')
          .bind(deviceId)
          .run();
        return new Response(JSON.stringify({ credits: 0, banned: false, subscription_enabled: true }), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      if (result.is_banned === 1) {
        return new Response(JSON.stringify({ credits: 0, banned: true, subscription_enabled: true }), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      return new Response(JSON.stringify({ credits: result.credits, banned: false, subscription_enabled: true }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    // ==========================================
    // 4. Consume credits
    // ==========================================
    if (url.pathname === '/api/consume-credits' && request.method === 'POST') {
      // Subscription disabled — no-op success.
      if (!subscriptionEnabled) {
        return new Response(
          JSON.stringify({ success: true, credits: -1, subscription_enabled: false }),
          { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      try {
        const { deviceId, amount } = (await request.json()) as { deviceId: string; amount: number };

        if (!deviceId || typeof amount !== 'number' || amount <= 0) {
          return new Response(JSON.stringify({ success: false, msg: 'Invalid request' }), {
            status: 400,
            headers: corsHeaders,
          });
        }

        const result: any = await env.blackout_db
          .prepare('SELECT credits, is_banned FROM licenses WHERE device_id = ?')
          .bind(deviceId)
          .first();

        if (result?.is_banned === 1) {
          return new Response(JSON.stringify({ success: false, msg: 'Banned' }), {
            headers: corsHeaders,
          });
        }

        const currentCredits = result ? (result.credits as number) : 0;
        if (currentCredits < amount) {
          return new Response(JSON.stringify({ success: false, msg: 'Not enough credits' }), {
            headers: corsHeaders,
          });
        }

        const newCredits = currentCredits - amount;
        await env.blackout_db
          .prepare('UPDATE licenses SET credits = ?, total_sms_sent = total_sms_sent + ? WHERE device_id = ?')
          .bind(newCredits, amount, deviceId)
          .run();

        return new Response(JSON.stringify({ success: true, credits: newCredits }), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      } catch (e) {
        return new Response(JSON.stringify({ success: false }), {
          status: 400,
          headers: corsHeaders,
        });
      }
    }

    // ==========================================
    // 5. NOWPayments webhook (verified)
    // ==========================================
    if (url.pathname === '/api/nowpayments-webhook' && request.method === 'POST') {
      const bodyText = await request.text();
      const signature = request.headers.get('x-nowpayments-sig');

      if (!signature) {
        return new Response('Missing signature', { status: 401 });
      }

      let payload: any;
      try {
        payload = JSON.parse(bodyText);
      } catch {
        return new Response('Invalid JSON', { status: 400 });
      }

      const sortedBody = JSON.stringify(sortObjectDeep(payload));

      if (!(await verifySignature(sortedBody, signature, env.NOWPAYMENTS_IPN_SECRET))) {
        return new Response('Invalid signature', { status: 401 });
      }

      const invoiceId = payload.invoice_id?.toString() || payload.payment_id?.toString();
      if (!invoiceId) {
        return new Response('Missing invoice_id', { status: 400 });
      }

      const txRecord: any = await env.blackout_db
        .prepare('SELECT status, credits FROM transactions WHERE id = ?')
        .bind(invoiceId)
        .first();

      if (!txRecord) {
        return new Response('Transaction not found', { status: 404 });
      }

      if (txRecord.status === 'finished') {
        return new Response('Already processed', { status: 200 });
      }

      const paymentStatus: string = payload.payment_status;
      const deviceId: string = payload.order_id;

      if (!deviceId) {
        return new Response('Missing order_id', { status: 400 });
      }

      await env.blackout_db
        .prepare('UPDATE transactions SET status = ?, updated_at = datetime("now") WHERE id = ?')
        .bind(paymentStatus, invoiceId)
        .run();

      if (paymentStatus === 'finished') {
        // Ensure a license row exists before crediting (safe even if subscription was disabled)
        await env.blackout_db
          .prepare('INSERT OR IGNORE INTO licenses (device_id, credits, is_banned, total_sms_sent) VALUES (?, 0, 0, 0)')
          .bind(deviceId)
          .run();

        await env.blackout_db
          .prepare('UPDATE licenses SET credits = credits + ? WHERE device_id = ?')
          .bind(txRecord.credits, deviceId)
          .run();
      }

      return new Response('OK', { status: 200 });
    }

    // ==========================================
    // 🚨 Admin routes (require admin auth)
    // ==========================================
    if (url.pathname.startsWith('/api/admin/') && !isAdmin) {
      return new Response('Unauthorized', { status: 401, headers: corsHeaders });
    }

    if (url.pathname === '/api/admin/stats' && request.method === 'GET') {
      const usersCount: any = await env.blackout_db
        .prepare('SELECT COUNT(*) as c FROM licenses')
        .first();
      const totalSms: any = await env.blackout_db
        .prepare('SELECT SUM(total_sms_sent) as s FROM licenses')
        .first();
      const totalRevenue: any = await env.blackout_db
        .prepare("SELECT SUM(amount_usd) as r FROM transactions WHERE status = 'finished'")
        .first();

      return new Response(
        JSON.stringify({
          users: usersCount?.c || 0,
          sms: totalSms?.s || 0,
          revenue: totalRevenue?.r || 0,
        }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    if (url.pathname === '/api/admin/users' && request.method === 'GET') {
      const results = await env.blackout_db
        .prepare('SELECT * FROM licenses ORDER BY created_at DESC')
        .all();
      return new Response(JSON.stringify(results.results), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    if (url.pathname === '/api/admin/transactions' && request.method === 'GET') {
      const results = await env.blackout_db
        .prepare('SELECT * FROM transactions ORDER BY created_at DESC LIMIT 100')
        .all();
      return new Response(JSON.stringify(results.results), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    if (url.pathname === '/api/admin/user-action' && request.method === 'POST') {
      const { deviceId, action, value } = (await request.json()) as {
        deviceId: string;
        action: string;
        value: number;
      };

      if (!deviceId || !action) {
        return new Response(JSON.stringify({ success: false, error: 'Missing fields' }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      const validActions = ['ban', 'unban', 'delete', 'set_credits'];
      if (!validActions.includes(action)) {
        return new Response(
          JSON.stringify({ success: false, error: `Unknown action: ${action}` }),
          { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      if (action === 'set_credits' && (typeof value !== 'number' || value < 0)) {
        return new Response(
          JSON.stringify({ success: false, error: 'Invalid value for set_credits' }),
          { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      if (action === 'ban') {
        await env.blackout_db
          .prepare('UPDATE licenses SET is_banned = 1 WHERE device_id = ?')
          .bind(deviceId)
          .run();
      } else if (action === 'unban') {
        await env.blackout_db
          .prepare('UPDATE licenses SET is_banned = 0 WHERE device_id = ?')
          .bind(deviceId)
          .run();
      } else if (action === 'delete') {
        await env.blackout_db
          .prepare('DELETE FROM licenses WHERE device_id = ?')
          .bind(deviceId)
          .run();
      } else if (action === 'set_credits') {
        await env.blackout_db
          .prepare('UPDATE licenses SET credits = ? WHERE device_id = ?')
          .bind(value, deviceId)
          .run();
      }

      return new Response(JSON.stringify({ success: true }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    if (url.pathname === '/api/admin/plans' && request.method === 'GET') {
      const results = await env.blackout_db.prepare('SELECT * FROM plans').all();
      return new Response(JSON.stringify(results.results), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    if (url.pathname === '/api/admin/plans/update' && request.method === 'POST') {
      const { id, name, price, credits, is_active } = (await request.json()) as {
        id: string;
        name: string;
        price: number;
        credits: number;
        is_active: number;
      };

      if (!id) {
        return new Response(JSON.stringify({ success: false, error: 'Missing id' }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      await env.blackout_db
        .prepare('UPDATE plans SET name = ?, price_usd = ?, credits = ?, is_active = ? WHERE id = ?')
        .bind(name, price, credits, is_active, id)
        .run();

      return new Response(JSON.stringify({ success: true }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    return new Response('Not Found', { status: 404 });
  },
};

// ==========================================
// Helper Functions
// ==========================================

function sortObjectDeep(obj: any): any {
  if (Array.isArray(obj)) return obj.map(sortObjectDeep);
  if (obj !== null && typeof obj === 'object') {
    return Object.keys(obj)
      .sort()
      .reduce((acc: any, key: string) => {
        acc[key] = sortObjectDeep(obj[key]);
        return acc;
      }, {});
  }
  return obj;
}

async function verifySignature(
  body: string,
  signature: string,
  secret: string
): Promise<boolean> {
  const sigBytes = hexToUint8Array(signature);
  if (!sigBytes) return false;

  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(secret),
    { name: 'HMAC', hash: 'SHA-512' },
    false,
    ['verify']
  );

  return crypto.subtle.verify('HMAC', key, sigBytes, encoder.encode(body));
}

function hexToUint8Array(hex: string): Uint8Array | null {
  if (!hex || hex.length % 2 !== 0) return null;
  const matches = hex.match(/.{1,2}/g);
  if (!matches) return null;

  const bytes = new Uint8Array(matches.length);
  for (let i = 0; i < matches.length; i++) {
    const val = parseInt(matches[i], 16);
    if (isNaN(val)) return null;
    bytes[i] = val;
  }
  return bytes;
}