const { db } = require('../app.js');
const express = require('express');
const router = express.Router();

router.get('/:user_code', async function (req, res) {
  let docRef = await db.collection('pins').doc(req.params.user_code.toUpperCase()).get();

  if (!docRef.exists) {
    res.send({
      success: false,
      msg: "code_not_valid"
    });
    return;
  }
  res.send({
    success: true,
    verifyURL: docRef.data().verifyURL
  });
});

module.exports = router;