const { db } = require('../app.js');
var express = require('express');
var router = express.Router();

router.get('/', async function(req, res, next) {
  if (req.query.state === undefined || req.query.code === undefined) {
    return res.send({
      success: false,
      msg: "Code or state not present"
    })
  }
  var docRef = db.collection('pins').doc(req.query.state);

  await docRef.update({
    auth_code: req.query.code
  });
  res.sendFile(process.cwd() + '/views/success.html');
});

module.exports = router;