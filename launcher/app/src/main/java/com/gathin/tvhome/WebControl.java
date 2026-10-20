package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Phone control: a small web page on the home network for the launcher's own settings, so nobody needs
 * the remote or adb for them. One thread, one connection at a time, fixed routes only, every request
 * limited in size and time. Only addresses on the local network get an answer, and every request needs
 * the six-digit code the TV shows (Launcher settings, Phone control); a visit with the code sets a cookie.
 * Settings go to the same SharedPreferences Launcher settings uses; while Home is in front it applies them
 * at once through {@link #remote}, otherwise on its next resume. Off unless web_control is true.
 *
 * The page and /api/state are built in sections (one JSON key, one card each), so features that come
 * with their own settings can add theirs without touching the rest.
 */
final class WebControl {
    static final String TAG = "TvHomeWeb";

    /** Its own preferences file: a theme push clears launcher_settings choices, never the code. */
    private static final String PREFS = "web_control";
    static final String PREF_CODE = "code";
    private static final String COOKIE = "hk";
    private static final int READ_TIMEOUT_MS = 5000;
    static final int MAX_JSON = 4 * 1024;

    /** What Home does with the page's requests while it is in front; null otherwise. */
    interface Remote {
        void settingsChanged();
    }

    static volatile Remote remote;
    /** Settings changed while Home was not in front; it rebuilds on its next resume. */
    private static volatile boolean changed;

    /** True once after a change that Home has not applied yet. */
    static boolean takeChanged() {
        boolean c = changed;
        changed = false;
        return c;
    }

    private static volatile Thread thread;
    private static volatile ServerSocket server;
    private static volatile int port;
    private static volatile boolean broken;
    private static final WebHttp.Limiter limiter = new WebHttp.Limiter();
    private static String page;

    private WebControl() {
    }

    /** Starts the server once per process, or stops it when switched off. Never on the main thread's time. */
    static synchronized void start(Context ctx, boolean on, int wantedPort) {
        if (!on || broken) {
            stop();
            return;
        }
        if (thread != null) return;
        final Context app = ctx.getApplicationContext();
        port = wantedPort;
        Thread t = new Thread(() -> serve(app, wantedPort), "web-control");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        thread = t;
        t.start();
    }

    static synchronized void stop() {
        ServerSocket s = server;
        server = null;
        thread = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** Running and reachable on this port; 0 when off. */
    static int port() {
        return thread != null && !broken ? port : 0;
    }

    /** The access code, made the first time it is asked for. */
    static String code(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String c = p.getString(PREF_CODE, null);
        if (c == null || c.length() != 6) {
            c = WebHttp.newCode(new SecureRandom());
            p.edit().putString(PREF_CODE, c).apply();
        }
        return c;
    }

    /** A new code: phones that used the old link or cookie have to open the new link. */
    static String newCode(Context ctx) {
        String c = WebHttp.newCode(new SecureRandom());
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREF_CODE, c).apply();
        return c;
    }

    private static void serve(Context app, int wantedPort) {
        try (ServerSocket s = new ServerSocket(wantedPort)) {
            server = s;
            while (server == s) {
                try (Socket client = s.accept()) {
                    handle(app, client);
                } catch (IOException e) {
                    if (server != s) break;   // stopped
                } catch (Exception | LinkageError e) {
                    // One bad request never takes the page down, let alone Home.
                    Log.w(TAG, "request failed: " + e);
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "no server on port " + wantedPort + ": " + e);
        } catch (Exception | LinkageError e) {
            broken = true;
            Log.w(TAG, "phone control switched off for this session: " + e);
        } finally {
            synchronized (WebControl.class) {
                if (thread == Thread.currentThread()) thread = null;
            }
        }
    }

    private static void handle(Context ctx, Socket client) throws Exception {
        client.setSoTimeout(READ_TIMEOUT_MS);
        InetAddress from = client.getInetAddress();
        if (from == null || !WebHttp.local(from.getAddress())) return;   // not the home network: no answer
        String address = from.getHostAddress();
        InputStream in = new BufferedInputStream(client.getInputStream());
        OutputStream out = new BufferedOutputStream(client.getOutputStream());
        try {
            WebHttp.Request req;
            try {
                req = WebHttp.parseHead(WebHttp.readHead(in));
            } catch (WebHttp.BadRequest e) {
                send(out, e.status, "text/plain; charset=utf-8", e.getMessage().getBytes(StandardCharsets.UTF_8), null);
                return;
            }
            long now = System.currentTimeMillis();
            if (limiter.blocked(address, now)) {
                send(out, 429, "text/plain; charset=utf-8", bytes("Too many wrong codes. Wait a minute."), null);
                return;
            }
            String code = code(ctx);
            String given = req.query.get("k");
            String cookie = WebHttp.cookie(req.header("cookie"), COOKIE);
            boolean byLink = WebHttp.sameCode(given, code);
            boolean byCookie = WebHttp.sameCode(cookie, code);
            if (!byLink && !byCookie) {
                if (given != null || cookie != null) limiter.failed(address, now);
                send(out, 403, "text/html; charset=utf-8", bytes(DENIED), null);
                return;
            }
            if (byLink && req.method.equals("GET") && req.path.equals("/")) {
                // Swap the code in the address bar for a cookie, so it isn't left in the phone's history.
                String headers = "Location: /\r\nSet-Cookie: " + COOKIE + "=" + code + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=31536000\r\n";
                send(out, 303, "text/plain; charset=utf-8", bytes("See /"), headers);
                return;
            }
            route(ctx, req, in, out);
        } finally {
            out.flush();
        }
    }

    private static void route(Context ctx, WebHttp.Request req, InputStream in, OutputStream out) throws Exception {
        String key = req.method + " " + req.path;
        if (key.equals("GET /")) {
            send(out, 200, "text/html; charset=utf-8", bytes(page(ctx)), CSP);
            return;
        }
        if (req.method.equals("POST") && !"tvhome".equals(req.header("x-requested-with"))) {
            // Only the page's own script sends this header; a form on another site can't.
            send(out, 403, "text/plain; charset=utf-8", bytes("Missing X-Requested-With"), null);
            return;
        }
        switch (key) {
            case "GET /api/state":
                sendJson(out, 200, state(ctx));
                break;
            case "POST /api/settings":
                settings(ctx, req, in, out);
                break;
            default:
                sendError(out, 404, "Not found");
        }
    }

    // ---- settings ---------------------------------------------------------------------------------

    /**
     * {"layout": {...}, "home": bool}: one key per section of the page. "layout" is what Launcher settings
     * offers on the TV: columns, animations, advanced effects, and whether the theme decides them.
     */
    private static String state(Context ctx) {
        Config cfg = Config.load(ctx);   // the theme's values with the choices made on the TV applied
        SharedPreferences choices = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        boolean followTheme = !choices.contains("columns") && !choices.contains("animations")
                && !choices.contains("advanced_effects");
        return "{\"layout\":{\"columns\":" + cfg.columns
                + ",\"animations\":" + cfg.animations
                + ",\"advanced_effects\":" + (cfg.animations && cfg.advancedEffects)
                + ",\"follow_theme\":" + followTheme
                + "},\"home\":" + (remote != null) + "}";
    }

    /**
     * {"layout": {"columns": 4..7, "animations": bool, "advanced_effects": bool, "follow_theme": true}}, any
     * subset, applied the way Launcher settings applies them: advanced effects need animations, and
     * follow_theme drops the choices so the theme's values come back.
     */
    private static void settings(Context ctx, WebHttp.Request req, InputStream in, OutputStream out) throws Exception {
        long length = req.contentLength();
        if (length < 0 || length > MAX_JSON) {
            sendError(out, 413, "Settings are at most " + MAX_JSON + " bytes");
            return;
        }
        JSONObject layout;
        try {
            JSONObject o = new JSONObject(new String(readBody(in, (int) length), StandardCharsets.UTF_8));
            layout = o.optJSONObject("layout");
        } catch (Exception e) {
            sendError(out, 400, "Not JSON");
            return;
        }
        if (layout != null) {
            SharedPreferences choices = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
            if (layout.optBoolean("follow_theme", false)) {
                choices.edit().remove("columns").remove("animations").remove("advanced_effects").commit();
            } else {
                Config cfg = Config.load(ctx);
                SharedPreferences.Editor e = choices.edit();
                Object columns = layout.opt("columns");
                if (columns instanceof Integer) {
                    int c = (Integer) columns;
                    if (c < 4 || c > 7) {
                        sendError(out, 400, "Columns are 4 to 7");
                        return;
                    }
                    e.putInt("columns", c);
                }
                boolean animations = cfg.animations;
                if (layout.opt("animations") instanceof Boolean) {
                    animations = layout.optBoolean("animations");
                    e.putBoolean("animations", animations);
                    if (!animations) e.putBoolean("advanced_effects", false);
                }
                if (layout.opt("advanced_effects") instanceof Boolean) {
                    e.putBoolean("advanced_effects", animations && layout.optBoolean("advanced_effects"));
                }
                e.commit();
            }
        }
        Remote r = remote;
        if (r != null) r.settingsChanged();
        else changed = true;
        sendJson(out, 200, state(ctx));
    }

    // ---- HTTP plumbing ----------------------------------------------------------------------------

    private static byte[] readBody(InputStream in, int length) throws IOException {
        byte[] b = new byte[length];
        int off = 0;
        while (off < length) {
            int n = in.read(b, off, length - off);
            if (n < 0) throw new IOException("body cut short");
            off += n;
        }
        return b;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static void sendJson(OutputStream out, int status, String json) throws IOException {
        send(out, status, "application/json; charset=utf-8", bytes(json), null);
    }

    private static void sendError(OutputStream out, int status, String message) throws IOException {
        sendJson(out, status, "{\"error\":" + WebHttp.json(message) + "}");
    }

    private static final String CSP = "Content-Security-Policy: default-src 'none'; img-src 'self'; "
            + "style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; form-action 'none'; frame-ancestors 'none'\r\n";

    private static void send(OutputStream out, int status, String type, byte[] body, String extra) throws IOException {
        StringBuilder h = new StringBuilder(256);
        h.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n")
                .append("Content-Type: ").append(type).append("\r\n")
                .append("Content-Length: ").append(body.length).append("\r\n")
                .append("Connection: close\r\n")
                .append("X-Content-Type-Options: nosniff\r\n")
                .append("Referrer-Policy: no-referrer\r\n")
                .append("X-Frame-Options: DENY\r\n");
        if (extra == null || !extra.contains("Cache-Control")) h.append("Cache-Control: no-store\r\n");
        if (extra != null) h.append(extra);
        h.append("\r\n");
        out.write(h.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
    }

    private static String reason(int status) {
        switch (status) {
            case 200:
                return "OK";
            case 303:
                return "See Other";
            case 400:
                return "Bad Request";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            case 405:
                return "Method Not Allowed";
            case 413:
                return "Payload Too Large";
            case 429:
                return "Too Many Requests";
            case 431:
                return "Request Header Fields Too Large";
            case 501:
                return "Not Implemented";
            default:
                return "Error";
        }
    }

    private static synchronized String page(Context ctx) throws IOException {
        if (page == null) {
            try (InputStream in = ctx.getResources().openRawResource(R.raw.web_control)) {
                ByteArrayOutputStream b = new ByteArrayOutputStream(16 * 1024);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                page = new String(b.toByteArray(), StandardCharsets.UTF_8);
            }
        }
        return page;
    }

    private static final String DENIED = "<!doctype html><meta charset=utf-8>"
            + "<meta name=viewport content='width=device-width,initial-scale=1'><title>Home</title>"
            + "<body style='background:#000;color:#ddd;font:16px system-ui,sans-serif;padding:32px'>"
            + "<h2 style='font-weight:500'>Open the link shown on the TV</h2>"
            + "<p>On the TV, hold OK on Settings and open Launcher settings. Phone control shows this page's "
            + "address with its code, and a QR code for it.</p>";

    static String url(String ip, int port, String code) {
        return String.format(Locale.US, "http://%s:%d/?k=%s", ip, port, code);
    }
}
