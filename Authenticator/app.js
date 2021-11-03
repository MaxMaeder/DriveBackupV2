const admin = require('firebase-admin');
admin.initializeApp({
  credential: admin.credential.cert(JSON.parse(process.env.GOOGLE_CREDENTIALS))
});

const db = admin.firestore();
exports.db = db;

var express = require('express');
var compression = require('compression');

var pinRouter = require('./routes/pin');
var providerRouter = require('./routes/provider');
var callbackRouter = require('./routes/callback');
var tokenRouter = require('./routes/token');

var app = express();

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

app.get('/', function (req, res) {
  res.sendFile(__dirname + '/views/index.html');
});

app.get('/privacy-policy', function(req, res) {
  res.sendFile(__dirname + '/views/privacy-policy.html');
});

app.get('/about', function(req, res) {
  res.sendFile(__dirname + '/views/about.html');
});

app.get('/:user_code', async function (req, res) {
  var docRef = await db.collection('pins').doc(req.params.user_code.toUpperCase()).get();
  
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
  var pinsRef = db.collection("pins");
  var allInvalidRefs = await pinsRef.where("timestamp", "<", Date.now() - 300000).get();
  if (allInvalidRefs.empty) {
    return;
  }

  allInvalidRefs.forEach(doc => doc.ref.delete());
}, 150000);