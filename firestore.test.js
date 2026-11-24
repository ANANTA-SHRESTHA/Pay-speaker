const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validAlertPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    amount: 500.0,
    currency: "NPR",
    formattedAmount: "500",
    senderName: "Ramesh Adhikari",
    channelName: "ESEWA",
    sourceLabel: "eSewa",
    packageName: "com.f1soft.esewa",
    rawTitle: "eSewa Payment Received",
    rawMessage: "You have received NPR 500 from Ramesh Adhikari on eSewa.",
    spokenText: "eSewa payment received, 500 Rupees from Ramesh Adhikari.",
    spoken: true,
    simulated: false,
    clientTimestamp: Date.now() - 2000,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validSettingsPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    merchantName: "Himalayan Cafe",
    language: "ENGLISH",
    speechRate: 1.0,
    speechPitch: 1.0,
    boostVolume: true,
    playChime: true,
    announceSender: true,
    announceSource: true,
    repeatTwice: false,
    enabledChannels: ["ESEWA", "KHALTI", "FONEPAY"],
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied all reads and writes on payment_alerts and merchant_settings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("payment_alerts").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("payment_alerts")
      .doc("alert_1")
      .set(validAlertPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("merchant_settings")
      .doc("default")
      .get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("merchant_settings")
      .doc("default")
      .set(validSettingsPayload(ALICE_UID))
  );
});

test("Authenticated owner: valid Kotlin create, query, partial update, and delete on payment_alerts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const alertsCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("payment_alerts");

  // Create with senderName present (no `id` key)
  await assertSucceeds(alertsCol.doc("alert_1").set(validAlertPayload(ALICE_UID)));

  // Create with optional senderName omitted (mirrors .filterValues { it != null })
  const noSenderPayload = validAlertPayload(ALICE_UID, { simulated: true });
  delete noSenderPayload.senderName;
  await assertSucceeds(alertsCol.doc("alert_2").set(noSenderPayload));

  // Mirror exact Kotlin query: orderBy("clientTimestamp", DESCENDING)
  await assertSucceeds(alertsCol.orderBy("clientTimestamp", "desc").get());

  // Mirror partial update of mutable fields only
  await assertSucceeds(
    alertsCol.doc("alert_1").update({
      spoken: true,
      spokenText: "Updated spoken sentence",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(alertsCol.doc("alert_2").delete());
});

test("Payment alerts security bounds: reject @DocumentId field, ghost fields, future timestamps, bad amounts, invalid IDs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const alertsCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("payment_alerts");

  await assertSucceeds(alertsCol.doc("alert_1").set(validAlertPayload(ALICE_UID)));

  // Reject `@DocumentId` id field inside document data
  await assertFails(
    alertsCol.doc("alert_with_id").set(validAlertPayload(ALICE_UID, { id: "alert_with_id" }))
  );

  // Reject ghost field on create and shadow update
  await assertFails(
    alertsCol.doc("alert_ghost").set(validAlertPayload(ALICE_UID, { isVerified: true }))
  );
  await assertFails(
    alertsCol.doc("alert_1").update({
      isVerified: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable field mutation (createdAt, clientTimestamp, userId)
  await assertFails(
    alertsCol.doc("alert_1").update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    alertsCol.doc("alert_1").update({
      clientTimestamp: Date.now(),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject future timestamp (> request.time)
  await assertFails(
    alertsCol
      .doc("alert_future")
      .set(validAlertPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) }))
  );

  // Reject invalid amount, invalid channel, oversized message, invalid document ID
  await assertFails(
    alertsCol.doc("alert_zero").set(validAlertPayload(ALICE_UID, { amount: 0 }))
  );
  await assertFails(
    alertsCol.doc("alert_bad_chan").set(validAlertPayload(ALICE_UID, { channelName: "UNKNOWN" }))
  );
  await assertFails(
    alertsCol
      .doc("alert_big_msg")
      .set(validAlertPayload(ALICE_UID, { rawMessage: "x".repeat(1001) }))
  );
  await assertFails(
    alertsCol.doc("bad id with spaces!").set(validAlertPayload(ALICE_UID))
  );
});

test("Cross-user isolation: Alice cannot read, query, create, update, or delete Bob's payment_alerts", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("payment_alerts")
      .doc("bob_alert_1")
      .set(validAlertPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobAlerts = aliceDb
    .collection("users")
    .doc(BOB_UID)
    .collection("payment_alerts");

  await assertFails(bobAlerts.orderBy("clientTimestamp", "desc").get());
  await assertFails(bobAlerts.doc("bob_alert_1").get());
  await assertFails(bobAlerts.doc("alice_spoof").set(validAlertPayload(ALICE_UID)));
  await assertFails(
    bobAlerts.doc("bob_alert_1").update({
      spoken: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobAlerts.doc("bob_alert_1").delete());

  // Also forbid writing Bob's UID inside Alice's own subcollection
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("payment_alerts")
      .doc("spoofed_uid")
      .set(validAlertPayload(BOB_UID))
  );
});

test("Merchant settings: owner CRUD & validation bounds, cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceSettingsRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("merchant_settings")
    .doc("default");

  await assertSucceeds(aliceSettingsRef.set(validSettingsPayload(ALICE_UID)));
  await assertSucceeds(aliceSettingsRef.get());
  await assertSucceeds(
    aliceSettingsRef.update({
      merchantName: "Everest Mart",
      speechRate: 1.2,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject out-of-range speechRate, invalid language, ghost field, immutable createdAt
  await assertFails(
    aliceSettingsRef.update({
      speechRate: 3.0,
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertFails(
    aliceSettingsRef.update({
      language: "FRENCH",
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertFails(
    aliceSettingsRef.update({
      role: "admin",
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertFails(
    aliceSettingsRef.update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Cross-user isolation
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("merchant_settings")
      .doc("default")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("merchant_settings")
      .doc("default")
      .update({
        merchantName: "Hijacked",
        updatedAt: new Date(Date.now() - 400),
      })
  );
});
