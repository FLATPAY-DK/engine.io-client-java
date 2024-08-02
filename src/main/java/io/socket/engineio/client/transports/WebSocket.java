package io.socket.engineio.client.transports;


import io.socket.engineio.client.Transport;
import io.socket.engineio.parser.Packet;
import io.socket.engineio.parser.Parser;
import io.socket.parseqs.ParseQS;
import io.socket.thread.EventThread;
import io.socket.yeast.Yeast;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocketListener;
import okio.ByteString;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;


public class WebSocket extends Transport {

    public static final String NAME = "websocket";

    private static final Logger logger = Logger.getLogger(WebSocket.class.getName());

    private okhttp3.WebSocket ws;

    public WebSocket(Options opts) {
        super(opts);
        this.name = NAME;
    }

    private Map<String, Object> getProperties(Object obj) {
        if(obj == null) return null;
        List<Field> fields = new ArrayList<>();
        for (var field : obj.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            fields.add(field);
        }
        Map<String, Object> map = new HashMap<>();
        for (var field : fields) {
	        try {
		        map.put(
		                field.getName(),
		                field.get(obj)
		        );
	        } catch (IllegalAccessException e) {
		        map.put(field.getName(), null);
	        }
        }
        return map;
    }

    protected void doOpen() {
        logger.fine("Opening websocket connection");
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (this.extraHeaders != null) {
            headers.putAll(this.extraHeaders);
        }
        this.emit(EVENT_REQUEST_HEADERS, headers);

        final WebSocket self = this;
        Request.Builder builder = new Request.Builder().url(uri());
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (String v : entry.getValue()) {
                builder.addHeader(entry.getKey(), v);
            }
        }
        final Request request = builder.build();
        ws = webSocketFactory.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(okhttp3.WebSocket webSocket, Response response) {
                logger.severe(
                        "Opened websocket connection - websocket: " + webSocket +
                                ((response != null)
                                ?  " response body: " + getProperties(response.body()) + " response headers: " + response.headers()
                                : "")
                );
	            final Map<String, List<String>> headers = response.headers().toMultimap();
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        self.emit(EVENT_RESPONSE_HEADERS, headers);
                        self.onOpen();
                    }
                });
            }

            @Override
            public void onMessage(okhttp3.WebSocket webSocket, final String text) {
                logger.severe("Websocket received message: " + text);
                if (text == null) {
                    return;
                }
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                    self.onData(text);
                    }
                });
            }

            @Override
            public void onMessage(okhttp3.WebSocket webSocket, final ByteString bytes) {
                logger.severe("Websocket received message: " + bytes);
                if (bytes == null) {
                    return;
                }
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        self.onData(bytes.toByteArray());
                    }
                });
            }

            @Override
            public void onClosed(okhttp3.WebSocket webSocket, int code, String reason) {
                logger.severe("Websocket received onClosed - reason: " + reason + " code: " + code);
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        self.onClose();
                    }
                });
            }

            @Override
            public void onFailure(okhttp3.WebSocket webSocket, final Throwable t, Response response) {
                logger.severe(
                "A Websocket error occurred exception: " + t +
                        ((response != null)
                        ? " response body: " + response.body() + " response headers: " + response.headers()
                        : "")
                );
                if (!(t instanceof Exception)) {
                    return;
                }
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        self.onError("websocket error", (Exception) t);
                    }
                });
            }
        });
    }

    protected void write(Packet[] packets) {
        final WebSocket self = this;
        this.writable = false;

        final Runnable done = new Runnable() {
            @Override
            public void run() {
                // fake drain
                // defer to next tick to allow Socket to clear writeBuffer
                EventThread.nextTick(new Runnable() {
                    @Override
                    public void run() {
                        self.writable = true;
                        self.emit(EVENT_DRAIN);
                    }
                });
            }
        };

        final int[] total = new int[]{packets.length};
        for (Packet packet : packets) {
            if (this.readyState != ReadyState.OPENING && this.readyState != ReadyState.OPEN) {
                // Ensure we don't try to send anymore packets if the socket ends up being closed due to an exception
                break;
            }

            Parser.encodePacket(packet, new Parser.EncodeCallback() {
                @Override
                public void call(Object packet) {
                    boolean packetSend = false;
                    try {
                        if (packet instanceof String) {
                             packetSend = self.ws.send((String) packet);
                        } else if (packet instanceof byte[]) {
                            packetSend = self.ws.send(ByteString.of((byte[]) packet));
                        }
                    } catch (IllegalStateException e) {
                        logger.fine("websocket closed before we could write");
                    }
                    if (!packetSend) {
                        logger.severe("Packet: " + packet + " could not be sent");
                    }
                    if (0 == --total[0]) done.run();
                }
            });
        }
    }

    protected void doClose() {
        if (ws != null) {
            logger.severe("Closing websocket connection");
            ws.close(1000, "");
            ws = null;
        }
    }

    protected String uri() {
        Map<String, String> query = this.query;
        if (query == null) {
            query = new HashMap<String, String>();
        }
        String schema = this.secure ? "wss" : "ws";
        String port = "";

        if (this.port > 0 && (("wss".equals(schema) && this.port != 443)
                || ("ws".equals(schema) && this.port != 80))) {
            port = ":" + this.port;
        }

        if (this.timestampRequests) {
            query.put(this.timestampParam, Yeast.yeast());
        }

        String derivedQuery = ParseQS.encode(query);
        if (derivedQuery.length() > 0) {
            derivedQuery = "?" + derivedQuery;
        }

        boolean ipv6 = this.hostname.contains(":");
        return schema + "://" + (ipv6 ? "[" + this.hostname + "]" : this.hostname) + port + this.path + derivedQuery;
    }
}