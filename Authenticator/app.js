const { cert, initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
initializeApp({ credential: cert(JSON.parse(process.env.GOOGLE_CREDENTIALS)) });

const db = getFirestore();
exports.db = db;

const express = require('express');
const compression = require('compression');

const pinRouter = require('./routes/pin');
const providerRouter = require('./routes/provider');
const callbackRouter = require('./routes/callback');
const tokenRouter = require('./routes/token');

let app = express();

app.use(compression());
app.use(express.json());
app.use(express.urlencoded({ extended: false }));
app.use(express.static(__dirname + '/public'));

const authMiddleware = (req, res, next) => {
  if (req.body.client_secret === process.env.AUTHENTICATOR_CLIENT_SECRET) {
    return next();
  }

  res.send({ success: false, msg: 'request_unauthenticated' });
}

app.use('/pin', authMiddleware, pinRouter);
app.use('/provider', providerRouter);
app.use('/callback', callbackRouter);
app.use('/token', authMiddleware, tokenRouter);

const pages = {
  "/": "index.html",
  "/privacy-policy": "privacy-policy.html",
  "/about": "about.html"
};

app.get(Object.keys(pages), (req, res) => {
  res.sendFile(`${__dirname}/views/${pages[req.path]}`);
});

app.get('/:user_code', async function (req, res) {
  let docRef = await db.collection('pins').doc(req.params.user_code.toUpperCase()).get();
  
  if (!docRef.exists) {
    res.send({ success: false, msg: "code_not_valid" });
  } else {
    res.redirect(docRef.data().verifyURL);
  }
})

app.listen(process.env.PORT, () => {
  console.log("App listening");
})

setInterval(async () => {
  let pinsRef = db.collection("pins");
  let allInvalidRefs = await pinsRef.where("timestamp", "<", Date.now() - 300000).get();

  if (!allInvalidRefs.empty) allInvalidRefs.forEach(doc => doc.ref.delete());
}, 150000);