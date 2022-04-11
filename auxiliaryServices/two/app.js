const express = require('express')
const fs = require('fs');
const cors = require('cors')
const morgan = require('morgan')

const rawdata1 = fs.readFileSync('example1.json');
const jsonExample1 = JSON.parse(rawdata1);

const app = express();
app.use(cors())
app.use(morgan('combined'))

const port = 3000;


app.get('/api/v2/planes/:plane_id/passengers', (req, res) => {
    jsonExample1[0]["company"] = req.params.plane_id;
    res.send(jsonExample1);
})

app.listen(port, () => {
    console.log(`Example app listening on port ${port}`);
})
