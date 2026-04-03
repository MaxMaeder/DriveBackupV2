const express = require('express');
let app = express();

app.use(express.json());
app.use(express.urlencoded({ extended: false }));
app.use(express.static(__dirname + '/public'));

let _db;
function getDb() {
  if (_db) return _db;
  const { cert, initializeApp } = require('firebase-admin/app');
  const { getFirestore } = require('firebase-admin/firestore');
  initializeApp({ credential: cert(JSON.parse(process.env.GOOGLE_CREDENTIALS)) });
  _db = getFirestore();
  return _db;
}
exports.getDb = getDb;

const authMiddleware = (req, res, next) => {
  if (req.body.client_secret === process.env.AUTHENTICATOR_CLIENT_SECRET) return next();
  res.send({ success: false, msg: 'request_unauthenticated' });
};

app.use('/pin',      authMiddleware, (req, res, next) => require('./routes/pin')(req, res, next));
app.use('/provider',               (req, res, next) => require('./routes/provider')(req, res, next));
app.use('/callback',               (req, res, next) => require('./routes/callback')(req, res, next));
app.use('/token',   authMiddleware, (req, res, next) => require('./routes/token')(req, res, next));

const pages = { "/": "index.html", "/privacy-policy": "privacy-policy.html", "/about": "about.html" };
app.get(Object.keys(pages), (req, res) => res.sendFile(`${__dirname}/views/${pages[req.path]}`));

app.get('/:user_code', async function (req, res) {
  const db = getDb();
  let docRef = await db.collection('pins').doc(req.params.user_code.toUpperCase()).get();
  
  if (!docRef.exists) {
    res.send({ success: false, msg: "code_not_valid" });
  } else {
    res.redirect(docRef.data().verifyURL);
  }
})

app.listen(process.env.PORT, () => console.log("App listening"));

setTimeout(() => {
  setInterval(async () => {
    if (!_db) return; // skip if firebase hasn't been touched yet
    const allInvalidRefs = await _db.collection("pins")
      .where("timestamp", "<", Date.now() - 300000).get();
    if (!allInvalidRefs.empty) allInvalidRefs.forEach(doc => doc.ref.delete());
  }, 150000);
}, 20000);