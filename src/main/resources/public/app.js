let currentRoom = "general";
let lastRendered = "";

function esc(s) {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
const chatSocket = new WebSocket(`${protocol}//${window.location.host}/chat`);

chatSocket.onclose = (event) => {
    console.warn("WebSocket closed. Code:", event.code, "Reason:", event.reason);
};

chatSocket.onerror = (error) => {
    console.error("WebSocket encountered an error:", error);
};

chatSocket.onmessage = function(event) {
  const m = JSON.parse(event.data);
  
  if (m.room !== currentRoom) return;

  const box = document.getElementById("messages");
  const nearBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 60;

  const emptyDiv = box.querySelector(".empty");
  if (emptyDiv) emptyDiv.remove();

  const t = new Date(m.createdAt).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });

  const msgHtml = `<div class="msg">
      <div class="head"><b>${esc(m.username)}</b> · ${t}</div>
      <div class="body">${esc(m.message)}</div>
    </div>`;
  
  box.insertAdjacentHTML('beforeend', msgHtml);

  if (nearBottom) box.scrollTop = box.scrollHeight;
};

async function loadRooms() {
  const rooms = await (await fetch("/api/rooms")).json();
  const div = document.getElementById("rooms");
  div.innerHTML = "";
  rooms.forEach((r) => {
    const btn = document.createElement("button");
    btn.textContent = "# " + r;
    btn.className = r === currentRoom ? "active" : "";
    btn.onclick = () => {
      currentRoom = r;
      lastRendered = "";
      loadRooms();
      loadMessages(); 
    };
    div.appendChild(btn);
  });
}

async function loadMessages() {
  try {
    const res = await fetch("/api/messages?room=" + currentRoom);
    const msgs = await res.json();

    const key = JSON.stringify(msgs);
    if (key === lastRendered) return;
    lastRendered = key;

    const box = document.getElementById("messages");
    const nearBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 60;

    if (msgs.length === 0) {
      box.innerHTML = '<div class="empty">No messages yet. Say hi!</div>';
      return;
    }

    box.innerHTML = msgs
      .map((m) => {
        const t = new Date(m.createdAt).toLocaleTimeString([], {
          hour: "2-digit",
          minute: "2-digit",
        });
        return `<div class="msg">
          <div class="head"><b>${esc(m.username)}</b> · ${t}</div>
          <div class="body">${esc(m.message)}</div>
        </div>`;
      })
      .join("");

    if (nearBottom) box.scrollTop = box.scrollHeight;
  } catch (e) {
    console.error("Failed to load message history:", e);
  }
}

document.getElementById("form").addEventListener("submit", (e) => {
  e.preventDefault();
  const username = document.getElementById("username").value.trim();
  const message = document.getElementById("message").value.trim();
  if (!username || !message) return;


  if (chatSocket.readyState !== WebSocket.OPEN) {
    alert("WebSocket connection lost. Please refresh the page to reconnect.");
    return;
  }

  chatSocket.send(JSON.stringify({ room: currentRoom, username, message }));

  document.getElementById("message").value = "";
  
});

loadRooms();
loadMessages();
