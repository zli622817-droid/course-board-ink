package com.eego.courseboardbeta;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ConfigServer extends Thread {
    private int port;
    private boolean running = true;
    private Context ctx;
    private WebView wv;
    private SharedPreferences sp;
    private ServerSocket ss;

    public ConfigServer(int port, Context ctx, WebView wv) {
        this.port = port;
        this.ctx = ctx;
        this.wv = wv;
        this.sp = ctx.getSharedPreferences("courseboard", Context.MODE_PRIVATE);
    }

    public void stopServer() {
        running = false;
        try { if (ss != null) ss.close(); } catch (Exception e) {}
        interrupt();
    }

    private String getCoursesJson() {
        return sp.getString("courses_json", "");
    }

    @Override
    public void run() {
        try {
            ss = new ServerSocket(port);
            while (running) {
                try {
                    final Socket s = ss.accept();
                    handle(s);
                } catch (Exception e) { if (!running) break; }
            }
            ss.close();
        } catch (Exception e) {}
    }

    private void handle(Socket s) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            String line = in.readLine();
            if (line == null) { s.close(); return; }
            String[] parts = line.split(" ");
            String method = parts[0];
            String path = parts[1];

            int contentLength = 0;
            String h;
            while ((h = in.readLine()) != null && h.length() > 0) {
                if (h.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(h.substring(15).trim());
                }
            }

            OutputStream out = s.getOutputStream();

            if (method.equals("GET") && path.equals("/")) {
                String html = pageHtml();
                send(out, 200, "text/html; charset=utf-8", html);
            } else if (method.equals("POST") && path.equals("/save")) {
                char[] buf = new char[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int r = in.read(buf, read, contentLength - read);
                    if (r < 0) break;
                    read += r;
                }
                final String json = new String(buf, 0, read);
                sp.edit().putString("courses_json", json).commit();
                String resp = "<!DOCTYPE html><html><head><meta charset='utf-8'><title>已保存</title></head>" +
                        "<body style='font-family:sans-serif;text-align:center;padding-top:80px;'>" +
                        "<h2>已保存</h2><p>墨水屏已自动刷新</p>" +
                        "<p><a href='/'>返回配置页</a></p>" +
                        "<script>setTimeout(function(){window.close();},2000);</script>" +
                        "</body></html>";
                send(out, 200, "text/html; charset=utf-8", resp);
                wv.post(new Runnable() {
                    public void run() {
                        wv.evaluateJavascript("loadExternalData()", null);
                    }
                });
            } else {
                send(out, 404, "text/plain", "not found");
            }
            s.close();
        } catch (Exception e) {
            try { s.close(); } catch (Exception ex) {}
        }
    }

    private void send(OutputStream out, int code, String contentType, String body) throws Exception {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + " OK\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + b.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(b);
        out.flush();
    }

    private String pageHtml() {
        String cur = getCoursesJson();
        String prompt = "你是一个课表JSON生成器。请把用户提供的课表（图片/表格/文字）转换成以下格式的JSON数组，不要输出任何其他内容：\n" +
                "[{\"day\":1,\"start\":1,\"end\":2,\"name\":\"课程名\",\"place\":\"教学楼教室\"}]\n" +
                "day: 1=周一,2=周二,...,7=周日\n" +
                "start/end: 大节编号 1-6（1=08:20,2=10:15,3=13:50,4=15:40,5=17:45,6=19:30）\n" +
                "name: 课程名简写（如：C语言、马原、工程力学）\n" +
                "place: 楼名+房间号（如：B203）\n" +
                "只输出JSON数组本身。";
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<title>课表配置</title><style>" +
                "body{font-family:sans-serif;padding:16px;background:#f5f5f5;margin:0}" +
                "h2{margin:0 0 12px}" +
                ".card{background:#fff;border-radius:8px;padding:14px;margin-bottom:12px}" +
                ".card h3{margin:0 0 8px;font-size:15px}" +
                "textarea{width:100%;height:120px;padding:10px;font-family:monospace;font-size:13px;border:1px solid #ccc;border-radius:6px;box-sizing:border-box;box-sizing:border-box}" +
                "#t{height:300px}" +
                "button{width:100%;padding:14px;background:#000;color:#fff;border:none;border-radius:6px;font-size:16px;margin-top:12px;cursor:pointer}" +
                ".tip{color:#666;font-size:13px;margin-bottom:8px;line-height:1.5}" +
                ".copy{background:#fff;color:#000;border:1px solid #000;margin-top:8px}" +
                "</style></head><body>" +
                "<h2>课表配置</h2>" +
                "<div class='card'>" +
                "<h3>使用说明</h3>" +
                "<div class='tip'>" +
                "1. 点「复制提示词」，发给AI（豆包/ChatGPT等）<br>" +
                "2. 附上课表截图或文件，让AI生成JSON<br>" +
                "3. 把AI返回的JSON粘贴到下方输入框<br>" +
                "4. 点「保存到墨水屏」，墨水屏自动刷新<br>" +
                "当前已预填本机课表，可直接修改" +
                "</div>" +
                "</div>" +
                "<div class='card'>" +
                "<h3>AI 辅助生成</h3>" +
                "<div class='tip'>把下面提示词复制给 AI，附上课表截图/文件，让 AI 生成 JSON，再粘贴到下方保存。</div>" +
                "<textarea id='prompt' readonly>" + prompt + "</textarea>" +
                "<button class='copy' onclick='copyPrompt()'>复制提示词</button>" +
                "</div>" +
                "<div class='card'>" +
                "<h3>课表 JSON</h3>" +
                "<textarea id='t'>" + cur + "</textarea>" +
                "<button onclick='save()'>保存到墨水屏</button>" +
                "<button class='copy' onclick='clearAll()'>清空</button>" +
                "</div>" +
                "<script>" +
                "function copyPrompt(){" +
                "  var t=document.getElementById('prompt');t.select();document.execCommand('copy');" +
                "  alert('已复制，去粘贴给AI');" +
                "}" +
                "function clearAll(){document.getElementById('t').value='';}" +
                "function save(){" +
                "  var v=document.getElementById('t').value;" +
                "  var x=new XMLHttpRequest();" +
                "  x.open('POST','/save',true);" +
                "  x.onreadystatechange=function(){" +
                "    if(x.readyState==4&&x.status==200){document.body.innerHTML='<h2 style=\"text-align:center;margin-top:80px;\">已保存，墨水屏已刷新</h2>';setTimeout(function(){window.close();},1500);}" +
                "  };" +
                "  x.send(v);" +
                "}" +
                "</script></body></html>";
    }
}
