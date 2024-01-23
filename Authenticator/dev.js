const express = require('express');
const compression = require('compression');

const app = express();

app.use(compression());
app.use(express.json());
app.use(express.urlencoded({ extended: false }));
app.use(express.static(__dirname + '/public'));

app.get('/', function (req, res) {
   res.sendFile(__dirname + '/views/index.html');
 });
 
 app.get('/privacy-policy', function(req, res) {
   res.sendFile(__dirname + '/views/privacy-policy.html');
 });
 
 app.get('/about', function(req, res) {
   res.sendFile(__dirname + '/views/about.html');
 });

app.listen(8080, () => {
   console.log("App listening");
})