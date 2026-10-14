const test = require("node:test");
const assert = require("node:assert");
const c = require("./codes");

test("normaliza e-mail", () => {
  assert.equal(c.normalizeEmail("  Ana@Gmail.COM "), "ana@gmail.com");
  assert.equal(c.normalizeEmail("sem-arroba"), null);
  assert.equal(c.normalizeEmail(42), null);
});

test("valida @", () => {
  assert.ok(c.validUsername("pushlife_fan.1"));
  assert.ok(!c.validUsername("1abc"));
  assert.ok(!c.validUsername("ab"));
  assert.ok(!c.validUsername("Maiuscula"));
});

test("código tem 6 dígitos", () => {
  for (let i = 0; i < 200; i++) assert.match(c.newCode(), /^\d{6}$/);
});

test("espera 1 minuto entre envios e no máximo 5 por hora", () => {
  const now = 10_000_000;
  assert.deepEqual(c.sendDecision(null, now), { windowStart: now, sent: 1 });
  assert.equal(c.sendDecision({ lastSentAt: now - 30_000, windowStart: now, sent: 1 }, now).error, "WAIT");
  assert.equal(c.sendDecision({ lastSentAt: now - 120_000, windowStart: now - 600_000, sent: 5 }, now).error, "TOO_MANY");
  assert.deepEqual(c.sendDecision({ lastSentAt: now - 120_000, windowStart: now - 4_000_000, sent: 5 }, now), { windowStart: now, sent: 1 });
});

test("confere o código", () => {
  const email = "ana@gmail.com";
  const saved = { codeHash: c.codeHash("123456", email), purpose: "login", expiresAt: 2000, attempts: 0 };
  assert.equal(c.checkCode(saved, { code: "123456", email, purpose: "login", now: 1000 }), "OK");
  assert.equal(c.checkCode(saved, { code: "654321", email, purpose: "login", now: 1000 }), "CODE_WRONG");
  assert.equal(c.checkCode(saved, { code: "123456", email, purpose: "signup", now: 1000 }), "CODE_EXPIRED");
  assert.equal(c.checkCode(saved, { code: "123456", email, purpose: "login", now: 3000 }), "CODE_EXPIRED");
  assert.equal(c.checkCode({ ...saved, attempts: 5 }, { code: "123456", email, purpose: "login", now: 1000 }), "TOO_MANY");
  assert.equal(c.checkCode(null, { code: "123456", email, purpose: "login", now: 1000 }), "CODE_EXPIRED");
});
