const express = require('express');
const http = require('http');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: "*" }
});

io.on("connection", (socket) => {
  console.log("⚡ User connected:", socket.id);

  socket.on("join_room", (room) => {
    socket.join(room);
    console.log(`📥 User ${socket.id} joined room: ${room}`);
  });

  socket.on("send_message", (data) => {
    console.log(`💬 ${data.sender}@${data.room}: ${data.message}`);
    io.to(data.room).emit("receive_message", {
      sender: data.sender,
      message: data.message
    });
  });

  // Optional: let server send messages to client every 10s
  setInterval(() => {
    socket.emit("receive_message", {
      sender: "Server",
      message: `Ping from server at ${new Date().toLocaleTimeString()}`
    });
  }, 10000);
});

server.listen(3000, () => {
  console.log("🚀 Server listening on http://localhost:3000");
});
