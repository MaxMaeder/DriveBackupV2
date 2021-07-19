const { db } = require('../app.js');
const alphabet = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';

var express = require('express');
var router = express.Router();

var interval = 5;

router.post('/', async function(req, res, next) {
  var user_code = nanoid(3) + '-' + nanoid(3);
  var device_code = nanoid(32);
  
  var verifyURL = '';
  
  switch (req.body.type) {
    case ('googledrive'):
      verifyURL = `https://accounts.google.com/o/oauth2/v2/auth?scope=https://www.googleapis.com/auth/drive&access_type=offline&prompt=consent&response_type=code&state=${user_code}&redirect_uri=https://drivebackup.web.app/callback&client_id=642273039492-b3mp4mvjovp0f7vanmf01mrukhp9i830.apps.googleusercontent.com`;
      break;
    case ('dropbox'):
      verifyURL = `https://www.dropbox.com/oauth2/authorize?token_access_type=offline&response_type=code&client_id=***REMOVED***&redirect_uri=https://drivebackup.web.app/callback&state=${user_code}`;
      break;
    default:
      return res.send({success: false, msg: "invalid_type"});
  }
  
  res.send({
    success: true,
    user_code,
    device_code,
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