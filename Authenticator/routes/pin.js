const { db } = require('../app.js');
const alphabet = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';

const express = require('express');
const router = express.Router();

const AUTH_URL = "https://auth.drivebackupv2.com";

const interval = 5;

router.post('/', async function (req, res) {
  const user_code = nanoid(3) + '-' + nanoid(3);
  const device_code = nanoid(32);

  let verifyURL = '';

  switch (req.body.type) {
    case ('googledrive'):
      verifyURL = `https://accounts.google.com/o/oauth2/v2/auth?scope=https://www.googleapis.com/auth/drive.file&access_type=offline&prompt=consent&response_type=code&state=${user_code}&redirect_uri=${AUTH_URL}/callback&client_id=602937851350-q69l9u3njis7nhb15cb7qmddqtrmhrg7.apps.googleusercontent.com`;
      break;
    case ('dropbox'):
      verifyURL = `https://www.dropbox.com/oauth2/authorize?token_access_type=offline&response_type=code&client_id=9as745vm8v7g0rr&redirect_uri=${AUTH_URL}/callback&state=${user_code}`;
      break;
    case ('onedrive'):
      verifyURL = `https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id=52e1b74e-7f53-41a7-aa0a-a9e9497726f8&scope=files.readwrite%20offline_access&response_type=code&redirect_uri=${AUTH_URL}/callback&state=${user_code}`;
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
    verification_uri: `${AUTH_URL}/`,
    interval
  });

  const docRef = db.collection('pins').doc(user_code);

  await docRef.set({
    device_code: device_code,
    timestamp: Date.now(),
    verifyURL: verifyURL,
    type: req.body.type
  });
});

const nanoid = (size = 21) => {
  let id = ''
  let i = size
  while (i--) {
    id += alphabet[(Math.random() * alphabet.length) | 0]
  }
  return id
}

module.exports = router;
