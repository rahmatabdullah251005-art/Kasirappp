const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const { serverTimestamp } = require("/usr/local/lib/node_modules/@firebase/rules-unit-testing/node_modules/firebase/firestore");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-umtazkiy-7b3a07a4-ff9f-4e19-80f7-b37dce770fb4";
const OWNER_UID = "owner_uid_1";
const KASIR_UID = "kasir_uid_2";
const STRANGER_UID = "stranger_uid_3";
const STORE_ID = "store_um_tazkiyah";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

async function seedStoreAndProduct() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("stores").doc(STORE_ID).set({
      ownerUid: OWNER_UID,
      storeName: "UM TAZKIYAH",
      qrisPayload: "000201010211UMTAZKIYAH",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    await db.collection("stores").doc(STORE_ID).collection("members").doc(OWNER_UID).set({
      uid: OWNER_UID,
      storeId: STORE_ID,
      ownerUid: OWNER_UID,
      storeName: "UM TAZKIYAH",
      fullName: "Owner UM Tazkiyah",
      role: "OWNER",
      isActive: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    await db.collection("stores").doc(STORE_ID).collection("members").doc(KASIR_UID).set({
      uid: KASIR_UID,
      storeId: STORE_ID,
      ownerUid: OWNER_UID,
      storeName: "UM TAZKIYAH",
      fullName: "Aisyah Kasir",
      role: "KASIR",
      isActive: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    await db.collection("stores").doc(STORE_ID).collection("products").doc("prod_1").set({
      skuCode: "UMT-BJ-001",
      name: "Gamis Toyobo",
      category: "BAJU",
      variantInfo: "Ukuran M",
      costPrice: 120000,
      sellingPrice: 180000,
      stock: 10,
      minStockAlert: 3,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  });
}

test("Unauthenticated user: cannot read stores or products", async () => {
  await seedStoreAndProduct();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("stores").doc(STORE_ID).get());
  await assertFails(unauthDb.collection("stores").doc(STORE_ID).collection("products").get());
});

test("Stranger: cannot read or write owner's store, products, or transactions", async () => {
  await seedStoreAndProduct();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID, { email: "stranger@test.com", email_verified: true }).firestore();
  await assertFails(strangerDb.collection("stores").doc(STORE_ID).get());
  await assertFails(strangerDb.collection("stores").doc(STORE_ID).collection("products").get());
  await assertFails(
    strangerDb.collection("stores").doc(STORE_ID).collection("members").doc(STRANGER_UID).set({
      uid: STRANGER_UID,
      storeId: STORE_ID,
      ownerUid: OWNER_UID,
      storeName: "UM TAZKIYAH",
      fullName: "Hacker",
      role: "KASIR",
      isActive: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});

test("Owner: can create store, products, and list all transactions", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: "owner@test.com", email_verified: true }).firestore();
  await assertSucceeds(
    ownerDb.collection("stores").doc(STORE_ID).set({
      ownerUid: OWNER_UID,
      storeName: "UM TAZKIYAH",
      qrisPayload: "000201010211UMTAZKIYAH",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    ownerDb.collection("stores").doc(STORE_ID).collection("products").doc("prod_1").set({
      skuCode: "UMT-BJ-001",
      name: "Gamis Toyobo",
      category: "BAJU",
      costPrice: 120000,
      sellingPrice: 180000,
      stock: 10,
      minStockAlert: 3,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  // Can create product with optional discountPercent
  await assertSucceeds(
    ownerDb.collection("stores").doc(STORE_ID).collection("products").doc("prod_promo").set({
      skuCode: "UMT-BJ-002",
      name: "Koko Kurta Promo",
      category: "BAJU",
      costPrice: 90000,
      sellingPrice: 140000,
      discountPercent: 15,
      stock: 5,
      minStockAlert: 2,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    ownerDb.collection("stores").where("ownerUid", "==", OWNER_UID).get()
  );
});

test("Owner: rejects ghost field in product write (Shadow Update test)", async () => {
  await seedStoreAndProduct();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertFails(
    ownerDb.collection("stores").doc(STORE_ID).collection("products").doc("prod_2").set({
      skuCode: "UMT-BK-001",
      name: "Buku Sirah",
      category: "BUKU",
      costPrice: 80000,
      sellingPrice: 120000,
      stock: 5,
      minStockAlert: 2,
      ghostAdminFlag: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});

test("Kasir: can read products, decrement stock on sale, and create own transaction; CANNOT change prices or delete products", async () => {
  await seedStoreAndProduct();
  const kasirDb = testEnv.authenticatedContext(KASIR_UID).firestore();

  // Can list products
  await assertSucceeds(kasirDb.collection("stores").doc(STORE_ID).collection("products").get());

  // Can decrement stock only
  await assertSucceeds(
    kasirDb.collection("stores").doc(STORE_ID).collection("products").doc("prod_1").update({
      stock: 8,
      updatedAt: serverTimestamp(),
    })
  );

  // Cannot change sellingPrice or costPrice
  await assertFails(
    kasirDb.collection("stores").doc(STORE_ID).collection("products").doc("prod_1").update({
      stock: 7,
      sellingPrice: 1000,
      updatedAt: serverTimestamp(),
    })
  );

  // Cannot delete product
  await assertFails(
    kasirDb.collection("stores").doc(STORE_ID).collection("products").doc("prod_1").delete()
  );

  // Can create own transaction
  await assertSucceeds(
    kasirDb.collection("stores").doc(STORE_ID).collection("transactions").doc("tx_1").set({
      invoiceNumber: "INV-20261114-0001",
      timestampMillis: Date.now(),
      dateKey: "2026-11-14",
      totalItems: 2,
      totalRevenue: 360000,
      discountAmount: 20000,
      finalRevenue: 340000,
      totalCost: 240000,
      netProfit: 100000,
      paymentMethod: "QRIS",
      amountPaid: 340000,
      changeAmount: 0,
      cashierUid: KASIR_UID,
      cashierName: "Aisyah",
      cashierRole: "KASIR",
      createdAt: serverTimestamp(),
    })
  );

  // Cannot list all transactions without cashierUid filter (rules are not filters)
  await assertFails(
    kasirDb.collection("stores").doc(STORE_ID).collection("transactions").get()
  );

  // Can list own transactions with cashierUid filter
  await assertSucceeds(
    kasirDb.collection("stores").doc(STORE_ID).collection("transactions").where("cashierUid", "==", KASIR_UID).get()
  );
});

test("Email spoofing test: unverified email cannot read or accept store_invites", async () => {
  await seedStoreAndProduct();
  const NEW_KASIR_UID = "new_kasir_uid_4";
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: "owner@test.com", email_verified: true }).firestore();
  await assertSucceeds(
    ownerDb.collection("store_invites").doc("inv_1").set({
      storeId: STORE_ID,
      storeName: "UM TAZKIYAH",
      ownerUid: OWNER_UID,
      cashierEmail: "kasirbaru@test.com",
      cashierName: "Fatimah",
      status: "pending",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Spoofed email with email_verified: false MUST fail
  const spoofDb = testEnv.authenticatedContext(NEW_KASIR_UID, { email: "kasirbaru@test.com", email_verified: false }).firestore();
  await assertFails(spoofDb.collection("store_invites").doc("inv_1").get());

  // Verified email CAN read and accept, then join members
  const verifiedKasirDb = testEnv.authenticatedContext(NEW_KASIR_UID, { email: "kasirbaru@test.com", email_verified: true }).firestore();
  await assertSucceeds(verifiedKasirDb.collection("store_invites").doc("inv_1").get());
  await assertSucceeds(
    verifiedKasirDb.collection("store_invites").doc("inv_1").update({
      status: "accepted",
      acceptedUid: NEW_KASIR_UID,
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    verifiedKasirDb.collection("stores").doc(STORE_ID).collection("members").doc(NEW_KASIR_UID).set({
      uid: NEW_KASIR_UID,
      storeId: STORE_ID,
      ownerUid: OWNER_UID,
      storeName: "UM TAZKIYAH",
      fullName: "Fatimah",
      email: "kasirbaru@test.com",
      inviteId: "inv_1",
      role: "KASIR",
      isActive: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});
