// Regras dos códigos de verificação (sem Firebase, para dar para testar).
const crypto = require("crypto");

const CODE_TTL_MS = 10 * 60 * 1000; // o código vale 10 minutos
const RESEND_WAIT_MS = 60 * 1000; // 1 minuto entre um envio e outro
const MAX_SENDS_PER_HOUR = 5;
const MAX_ATTEMPTS = 5; // depois disso, só pedindo outro código

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const USERNAME_RE = /^[a-z][a-z0-9_.]{2,19}$/;

const sha256 = (text) => crypto.createHash("sha256").update(text).digest("hex");

/** E-mail como é guardado, ou null se não parecer um e-mail. */
function normalizeEmail(email) {
  if (typeof email !== "string") return null;
  const e = email.trim().toLowerCase();
  return e.length <= 120 && EMAIL_RE.test(e) ? e : null;
}

const validUsername = (u) => typeof u === "string" && USERNAME_RE.test(u);

/** Código de 6 dígitos aleatório (inclusive com zeros na frente). */
const newCode = () => String(crypto.randomInt(0, 1_000_000)).padStart(6, "0");

/** O código nunca é guardado puro, só o hash junto com o e-mail. */
const codeHash = (code, email) => sha256(`${code}:${email}`);

/**
 * Decide se dá para mandar outro código agora.
 * Retorna { error } ("WAIT" ou "TOO_MANY") ou { windowStart, sent } para guardar.
 */
function sendDecision(previous, now) {
  const d = previous || {};
  if (d.lastSentAt && now - d.lastSentAt < RESEND_WAIT_MS) return { error: "WAIT" };
  const sameWindow = d.windowStart && now - d.windowStart < 60 * 60 * 1000;
  const sent = sameWindow ? d.sent || 0 : 0;
  if (sent >= MAX_SENDS_PER_HOUR) return { error: "TOO_MANY" };
  return { windowStart: sameWindow ? d.windowStart : now, sent: sent + 1 };
}

/**
 * Confere um código digitado contra o que está guardado.
 * Retorna "OK", "CODE_WRONG", "CODE_EXPIRED" ou "TOO_MANY".
 */
function checkCode(saved, { code, email, purpose, now }) {
  if (!saved || !saved.codeHash || saved.purpose !== purpose || now > saved.expiresAt) return "CODE_EXPIRED";
  if ((saved.attempts || 0) >= MAX_ATTEMPTS) return "TOO_MANY";
  return saved.codeHash === codeHash(code, email) ? "OK" : "CODE_WRONG";
}

module.exports = {
  CODE_TTL_MS,
  MAX_ATTEMPTS,
  normalizeEmail,
  validUsername,
  newCode,
  codeHash,
  sendDecision,
  checkCode,
  sha256,
};
