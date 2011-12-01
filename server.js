var app = require('express').createServer(),
    io = require('socket.io').listen(app),
    logger = require('winston'),
    program = require('commander');
    
//  
// SETUP
//

logger.cli();
logger.default.transports.console.timestamp = true;
    
program.version('0.1')
    .option('-p, --port [num]', 'Set the server port (default 8080)')
    .option('-H, --disableheartbeats', 'Diable heartbeats')
    .parse(process.argv)
    
var server = "localhost";
if(program.args.length==1) {
    server = program.args[0];
} else if (program.args.length==0) {
    logger.warning("Defaulting to localhost.");
}

var port = 8080;
if(program.port) {
    logger.info("Setting port to " + program.port);
    port = program.port;
}

if(program.disableheartbeats) {
    io.set("heartbeats", false)
}

io.set("log level", 0);

//
// GLOBALS
//
var connectedUsersCount = 0;


//
// LISTENERS
//
app.listen(port);
io.sockets.on('connection', function(socket) {
    connectedUsersCount++;
    
    socket.on('chat', function(data) {
        io.sockets.emit('chat', {text:data.text});
    });
    
    socket.on('disconnect', function(data) {
        connectedUsersCount--;
    });
});

