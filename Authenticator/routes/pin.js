const { db } = require('../app.js');
const alphabet = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';

var express = require('express');
var router = express.Router();

var interval = 5;

router.post('/', async function (req, res) {
  var user_code = nanoid(3) + '-' + nanoid(3);
  var device_code = nanoid(32);

  var verifyURL = '';

  switch (req.body.type) {
    case ('googledrive'):
      verifyURL = `https://accounts.google.com/o/oauth2/v2/auth?scope=https://www.googleapis.com/auth/drive&access_type=offline&prompt=consent&response_type=code&state=${user_code}&redirect_uri=https://drivebackup.web.app/callback&client_id=602937851350-q69l9u3njis7nhb15cb7qmddqtrmhrg7.apps.googleusercontent.com`;
      break;
    case ('dropbox'):
      verifyURL = `https://www.dropbox.com/oauth2/authorize?token_access_type=offline&response_type=code&client_id=9as745vm8v7g0rr&redirect_uri=https://drivebackup.web.app/callback&state=${user_code}`;
      break;
    case ('onedrive'):
      verifyURL = `https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id=52e1b74e-7f53-41a7-aa0a-a9e9497726f8&scope=files.readwrite%20offline_access&response_type=code&redirect_uri=https://drivebackup.web.app/callback&state=${user_code}`;
      break;
    default:
      return res.send({
        success: false,
        msg: "invalid_type"
      });
  }

  res.send({
    success: true,
    user_code,
    device_code,
    verification_uri: "https://drivebackup.web.app/",
    interval
  });

  var docRef = db.collection('pins').doc(user_code);

  await docRef.set({
    device_code: device_code,
    timestamp: Date.now(),
    verifyURL: verifyURL,
    type: req.body.type
  });
});

let nanoid = (size = 21) => {
  let id = ''
  let i = size
  while (i--) {
    id += alphabet[(Math.random() * alphabet.length) | 0]
  }
  return id
}

module.exports = router;