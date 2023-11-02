const { db } = require('../app.js');
const express = require('express');
const fetch = (...args) => import('node-fetch').then(({default: fetch}) => fetch(...args));
const router = express.Router();

const AUTH_URL = "https://auth.drivebackupv2.com";

router.post('/', async function (req, res) {
  if (req.body.device_code === undefined || req.body.user_code === undefined) return res.send({
    success: false,
    msg: "missing_params"
  });

  const docRef = await db.collection('pins').doc(req.body.user_code.toUpperCase()).get();

  // since /pin response is sent before doc creation (for speeeed) clients could potentially poll before the doc exists
  if (!docRef.exists) return res.send({
    success: false,
    msg: "code_not_authenticated"
  });

  const doc = docRef.data();

  if (doc.auth_code === undefined) return res.send({
    success: false,
    msg: "code_not_authenticated"
  });

  if (doc.type === "googledrive") {
    fetch('https://oauth2.googleapis.com/token', {
        method: 'POST',
        body: JSON.stringify({
          client_id: process.env.GOOGLE_ID,
          client_secret: process.env.GOOGLE_SECRET,
          code: doc.auth_code,
          grant_type: 'authorization_code',
          redirect_uri: `${AUTH_URL}/callback`
        })
      })
      .then(res => res.json())
      .then(json => {
        if (json.refresh_token != null) {
          res.send({
            success: true,
            refresh_token: json.refresh_token
          });
        } else {
          res.send({
            success: false,
            msg: JSON.stringify(json)
          });
        }
      });
  } else if (doc.type === "dropbox") {
    fetch('https://api.dropbox.com/oauth2/token', {
        method: 'POST',
        body: new URLSearchParams({
          'client_id': process.env.DROPBOX_ID,
          'client_secret': process.env.DROPBOX_SECRET,
          'code': doc.auth_code,
          'grant_type': 'authorization_code',
          'redirect_uri': `${AUTH_URL}/callback`
        })
      })
      .then(res => res.json())
      .then(json => {
        if (json.refresh_token != null) {
          res.send({
            success: true,
            refresh_token: json.refresh_token
          });
        } else {
          res.send({
            success: false,
            msg: JSON.stringify(json)
          });
        }
      });
  } else if (doc.type === "onedrive") {
    fetch('https://login.microsoftonline.com/common/oauth2/v2.0/token', {
        method: 'POST',
        body: new URLSearchParams({
          'client_id': process.env.ONEDRIVE_ID,
          'client_secret': process.env.ONEDRIVE_SECRET,
          'code': doc.auth_code,
          'grant_type': 'authorization_code',
          'redirect_uri': `${AUTH_URL}/callback`
        })
      })
      .then(res => res.json())
      .then(json => {
        if (json.refresh_token != null) {
          res.send({
            success: true,
            refresh_token: json.refresh_token
          });
        } else {
          res.send({
            success: false,
            msg: JSON.stringify(json)
          });
        }
      });
  } else {
    res.send({
      success: false,
      msg: "wait_wut"
    });
  }
});

module.exports = router;