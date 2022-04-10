const express = require('express')
const fs = require('fs');
const cors = require('cors')
const morgan = require('morgan')

const rawdata = fs.readFileSync('example.json');
const jsonExample = JSON.parse(rawdata);

const app = express();
app.use(cors())
app.use(morgan('combined'))

const port = 3000;

app.get('/api/v1/cars', (req, res) => {
    res.send(jsonExample);
})

app.listen(port, () => {
    console.log(`Example app listening on port ${port}`);
})
