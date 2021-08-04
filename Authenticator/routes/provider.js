const { db } = require('../app.js');
var express = require('express');
var router = express.Router();

router.get('/:user_code', async function(req, res) {
  var docRef = await db.collection('pins').doc(req.params.user_code.toUpperCase()).get();
  
  if (!docRef.exists) {
    res.send({
      success: false,
      msg: "code_not_valid"
    });
  } else {
    res.send({
      success: true,
      verifyURL: docRef.data().verifyURL
    });
  }
});

module.exports = router;