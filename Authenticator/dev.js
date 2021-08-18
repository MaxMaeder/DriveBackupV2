var express = require('express');
var compression = require('compression');

var app = express();

app.use(compression());
app.use(express.json());
app.use(express.urlencoded({ extended: false }));
app.use(express.static(__dirname + '/public'));
app.use(express.static(__dirname + '/views'));

app.listen(8080, () => {
   console.log("App listening");
})