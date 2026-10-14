// Servidor do PushLife: manda um código de 6 dígitos por e-mail para criar conta e para entrar.
//
// requestCode({ purpose: "signup", email, username, lang })      → confere se o e-mail e o @ estão livres e manda o código
// requestCode({ purpose: "login", email, password, lang })       → confere a senha e manda o código
// verifyCode({ purpose: "signup", email, code, password, username, name }) → cria a conta e devolve { token }
// verifyCode({ purpose: "login", email, code })                  → devolve { token }
//
// O app entra com o token (signInWithCustomToken). Os erros vêm como HttpsError com a mensagem
// igual a uma chave conhecida pelo app (EMAIL_IN_USE, USERNAME_TAKEN, WRONG_CREDENTIALS, CODE_WRONG...).

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { setGlobalOptions } = require("firebase-functions/v2");
const { defineString } = require("firebase-functions/params");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");
const codes = require("./codes");

admin.initializeApp();
setGlobalOptions({ region: "southamerica-east1", maxInstances: 5 });

// Vêm do arquivo functions/.env, que o GitHub monta com os segredos na hora de publicar.
const GMAIL_USER = defineString("GMAIL_USER");
const GMAIL_APP_PASSWORD = defineString("GMAIL_APP_PASSWORD");
const WEB_API_KEY = defineString("WEB_API_KEY");

const db = admin.firestore();
const ANDROID_PACKAGE = "com.pushlife.app";
const ANDROID_CERT = "D9A7CFAA3096F3156D8F0FE2EF2EB15A1DB0C240"; // SHA-1 da chave que assina o APK

const fail = (code, key) => {
  throw new HttpsError(code, key);
};

function emailFrom(data) {
  const email = codes.normalizeEmail(data && data.email);
  if (!email) fail("invalid-argument", "BAD_EMAIL");
  return email;
}

async function userByEmail(email) {
  try {
    return await admin.auth().getUserByEmail(email);
  } catch (e) {
    if (e.code === "auth/user-not-found") return null;
    throw e;
  }
}

/** Confere e-mail e senha no próprio Firebase Authentication. */
async function passwordMatches(email, password) {
  const res = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${WEB_API_KEY.value()}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Android-Package": ANDROID_PACKAGE, "X-Android-Cert": ANDROID_CERT },
    body: JSON.stringify({ email, password, returnSecureToken: false }),
  });
  if (res.ok) return true;
  const body = await res.json().catch(() => ({}));
  const message = (body.error && body.error.message) || "";
  if (message.startsWith("TOO_MANY_ATTEMPTS")) fail("resource-exhausted", "TOO_MANY");
  if (!/INVALID|EMAIL_NOT_FOUND|USER_DISABLED/.test(message)) console.error("signInWithPassword falhou", res.status, message);
  return false;
}

// ---------- e-mail ----------

const TEXTS = {
  pt: {
    subject: (code) => `${code} é o seu código do PushLife`,
    signup: "Use este código para criar sua conta no PushLife:",
    login: "Use este código para entrar na sua conta do PushLife:",
    expires: "O código vale por 10 minutos.",
    ignore: "Se não foi você, é só ignorar este e-mail.",
  },
  en: {
    subject: (code) => `${code} is your PushLife code`,
    signup: "Use this code to create your PushLife account:",
    login: "Use this code to sign in to your PushLife account:",
    expires: "The code is valid for 10 minutes.",
    ignore: "If this wasn't you, just ignore this email.",
  },
  es: {
    subject: (code) => `${code} es tu código de PushLife`,
    signup: "Usa este código para crear tu cuenta de PushLife:",
    login: "Usa este código para entrar en tu cuenta de PushLife:",
    expires: "El código es válido por 10 minutos.",
    ignore: "Si no fuiste tú, ignora este correo.",
  },
};

let transport = null;
function mailer() {
  if (!transport) {
    transport = nodemailer.createTransport({
      service: "gmail",
      auth: { user: GMAIL_USER.value(), pass: GMAIL_APP_PASSWORD.value() },
    });
  }
  return transport;
}

async function sendCodeEmail(email, code, purpose, lang) {
  const t = TEXTS[lang] || TEXTS.en;
  const html = `
  <div style="font-family:Arial,Helvetica,sans-serif;background:#0f0f12;padding:32px 16px">
    <div style="max-width:460px;margin:0 auto;background:#1b1b20;border-radius:24px;padding:32px;color:#ffffff">
      <div style="font-size:28px;font-weight:900;font-style:italic;letter-spacing:-1px">PUSH<span style="color:#FF7A00">LIFE</span></div>
      <p style="font-size:16px;color:#d0d0d8;margin:24px 0 16px">${purpose === "signup" ? t.signup : t.login}</p>
      <div style="font-size:42px;font-weight:900;letter-spacing:10px;text-align:center;background:linear-gradient(135deg,#FF9A1F,#FF4E00);border-radius:18px;padding:18px 0;color:#ffffff">${code}</div>
      <p style="font-size:14px;color:#a0a0aa;margin:20px 0 4px">${t.expires}</p>
      <p style="font-size:14px;color:#a0a0aa;margin:0">${t.ignore}</p>
    </div>
  </div>`;
  await mailer().sendMail({
    from: `"PushLife" <${GMAIL_USER.value()}>`,
    to: email,
    subject: t.subject(code),
    text: `${purpose === "signup" ? t.signup : t.login} ${code}\n\n${t.expires}\n${t.ignore}`,
    html,
  });
}

// ---------- funções chamadas pelo app ----------

async function checkSignupFree(email, username) {
  if (!codes.validUsername(username)) fail("invalid-argument", "BAD_USERNAME");
  if (await userByEmail(email)) fail("already-exists", "EMAIL_IN_USE");
  if ((await db.doc(`usernames/${username}`).get()).exists) fail("already-exists", "USERNAME_TAKEN");
}

exports.requestCode = onCall(async (request) => {
  const data = request.data || {};
  const email = emailFrom(data);
  const purpose = data.purpose;

  if (purpose === "signup") {
    await checkSignupFree(email, data.username);
  } else if (purpose === "login") {
    const user = await userByEmail(email);
    if (!user || typeof data.password !== "string" || !(await passwordMatches(email, data.password))) {
      fail("permission-denied", "WRONG_CREDENTIALS");
    }
  } else {
    fail("invalid-argument", "BAD_PURPOSE");
  }

  const ref = db.doc(`emailCodes/${codes.sha256(email)}`);
  const code = codes.newCode();
  const now = Date.now();
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const decision = codes.sendDecision(snap.exists ? snap.data() : null, now);
    if (decision.error) fail("resource-exhausted", decision.error);
    tx.set(ref, {
      codeHash: codes.codeHash(code, email),
      purpose,
      expiresAt: now + codes.CODE_TTL_MS,
      attempts: 0,
      lastSentAt: now,
      windowStart: decision.windowStart,
      sent: decision.sent,
    });
  });

  try {
    await sendCodeEmail(email, code, purpose, data.lang);
  } catch (e) {
    console.error("Falha ao enviar e-mail", e);
    fail("unavailable", "EMAIL_FAILED");
  }
  return { ok: true };
});

exports.verifyCode = onCall(async (request) => {
  const data = request.data || {};
  const email = emailFrom(data);
  const purpose = data.purpose;
  const code = typeof data.code === "string" ? data.code.trim() : "";
  if (!/^\d{6}$/.test(code)) fail("invalid-argument", "CODE_WRONG");

  // No cadastro, confere tudo antes de gastar o código.
  if (purpose === "signup") {
    if (typeof data.password !== "string" || data.password.length < 6) fail("invalid-argument", "WEAK_PASSWORD");
    await checkSignupFree(email, data.username);
  }

  const ref = db.doc(`emailCodes/${codes.sha256(email)}`);
  const result = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const saved = snap.exists ? snap.data() : null;
    const verdict = codes.checkCode(saved, { code, email, purpose, now: Date.now() });
    if (verdict === "CODE_WRONG") tx.update(ref, { attempts: (saved.attempts || 0) + 1 });
    if (verdict === "OK") tx.update(ref, { codeHash: admin.firestore.FieldValue.delete() });
    return verdict;
  });
  if (result !== "OK") fail(result === "TOO_MANY" ? "resource-exhausted" : "permission-denied", result);

  let uid;
  if (purpose === "signup") {
    const name = typeof data.name === "string" ? data.name.trim().slice(0, 30) : "";
    let user;
    try {
      user = await admin.auth().createUser({ email, password: data.password, emailVerified: true, displayName: name || undefined });
    } catch (e) {
      if (e.code === "auth/email-already-exists") fail("already-exists", "EMAIL_IN_USE");
      if (e.code === "auth/invalid-password") fail("invalid-argument", "WEAK_PASSWORD");
      throw e;
    }
    uid = user.uid;
    // Reserva o @ numa transação: se alguém pegou no meio do caminho, desfaz a conta.
    try {
      await db.runTransaction(async (tx) => {
        const handleRef = db.doc(`usernames/${data.username}`);
        if ((await tx.get(handleRef)).exists) throw new Error("USERNAME_TAKEN");
        tx.create(handleRef, { uid });
        tx.set(db.doc(`users/${uid}`), {
          username: data.username,
          name,
          email,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
        });
      });
    } catch (e) {
      await admin.auth().deleteUser(uid).catch(() => {});
      if (e.message === "USERNAME_TAKEN" || e.code === 6 /* ALREADY_EXISTS */) fail("already-exists", "USERNAME_TAKEN");
      throw e;
    }
  } else if (purpose === "login") {
    const user = await userByEmail(email);
    if (!user) fail("permission-denied", "WRONG_CREDENTIALS");
    uid = user.uid;
  } else {
    fail("invalid-argument", "BAD_PURPOSE");
  }

  return { token: await admin.auth().createCustomToken(uid) };
});
