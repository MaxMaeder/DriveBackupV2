const { db } = require('../app.js');
var express = require('express');
var router = express.Router();

router.get('/', async function(req, res) {
  if (req.query.state === undefined || req.query.code === undefined || req.query.error != undefined) {
    res.sendFile(__dirname + '/views/fail.html');
  }
  var docRef = db.collection('pins').doc(req.query.state);

  if (!docRef.exists) return res.send({ success: false, msg: "code_not_authenticated"});

  await docRef.update({ auth_code: req.query.code });

  res.sendFile(process.cwd() + '/views/success.html');
});

module.exports = router;