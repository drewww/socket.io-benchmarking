var app = require('express').createServer(),
    io = require('socket.io').listen(app),
    logger = require('winston'),
    program = require('commander'),
    amqp = require('amqp');
    
//  
// SETUP
//

logger.cli();
logger.default.transports.console.timestamp = true;
    
program.version('0.1')
    .option('-p, --port [num]', 'Set the server port (default 8080)')
    .option('-H, --disableheartbeats', 'Disable heartbeats')
    .option('-q, --queuehost', 'Set the queue host.')
    .parse(process.argv)
    
var server = "localhost";
if(program.args.length==1) {
    server = program.args[0];
} else if (program.args.length==0) {
    logger.warn("Defaulting to localhost.");
}

var port = 8080;
if(program.port) {
    logger.info("Setting port to " + program.port);
    port = program.port;
}

var queueHost = "localhost";
if(program.queuehost) {
    logger.info("Setting queue host to " + program.queuehost);
    queueHost = program.queuehost;
}

// app.listen(port);

if(program.disableheartbeats) {
    io.set("heartbeats", false)
}

io.set("log level", 0);


//
// LISTENERS
//

app.get('/', function (req, res) {
  res.sendfile(__dirname + '/index.html');
});


//
// GLOBALS
//
var connectedUsersCount = 0;
var messagesPerSecond = 0;

setTimeout(logStatus, 1000);

var connection = amqp.createConnection({host:queueHost});
var exchange;

connection.on('ready', function() {
    var e = connection.exchange('socket-events',{"type":"topic"}, function(newExchange) {
        logger.info("Exchange " + newExchange.name + " is open for business.");
        
        exchange = newExchange;
        
        var q = connection.queue("", {"exclusive":true}, function(newQueue) {
            logger.info("Queue " + newQueue.name + " is open for business.");

            queue = newQueue;
            
            // Do binding setup.
            // The message hierarchy is going to be:
            // broadcast
            // room.room-name
            // user.user-id
            queue.bind(exchange.name, "from-user.*");

            queue.subscribe(dequeue);
        });
    });
});

//
// FUNCTIONS
//

function logStatus() {
    setTimeout(logStatus, 1000);
    
    
    logger.info("users: " + connectedUsersCount + "\tmessagesPerSecond: " + messagesPerSecond);
    messagesPerSecond = 0;
}

function dequeue(message,  headers, deliveryInfo) {
    // logger.info("Got a message with key: " + deliveryInfo.routingKey + " and message: ");
    // console.log(message);
    // console.log(headers);
    // console.log(deliveryInfo);
    // logger.info(message.name);
    // logger.info(message.args);
    // logger.info(message.args.text);
    var contents = JSON.parse(message.data.toString());
    
    // logger.info(contents.name);
    // logger.info(contents.args[0].text);
    
    switch(contents.name) {
        case 'chat':
            // Here's how we broadcast:
            exchange.publish("broadcast", message.data.toString());
            break;
        default:
            logger.warning("Received an unknown event type: " + contents.name);
            break;
    }


    messagesPerSecond++;
}