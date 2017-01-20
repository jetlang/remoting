package org.jetlang.web;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public interface MimeType {

    String getContentType();

    Charset getCharset(Path finalResource, byte[] bytes);

    class Text implements MimeType {

        private final String type;
        private final Charset charset;

        public Text(String type, Charset charset) {
            this.type = type;
            this.charset = charset;
        }

        @Override
        public String getContentType() {
            return type;
        }

        @Override
        public Charset getCharset(Path finalResource, byte[] bytes) {
            return charset;
        }
    }

    class Binary implements MimeType {
        private final String type;

        public Binary(String type) {
            this.type = type;
        }

        @Override
        public String getContentType() {
            return type;
        }

        @Override
        public Charset getCharset(Path finalResource, byte[] bytes) {
            return null;
        }
    }

    class Utf8Text extends Text {
        public static final Charset utf8 = Charset.forName("UTF-8");

        public Utf8Text(String type) {
            super(type, utf8);
        }

    }

    static Map<String, MimeType> createDefaultMimeTypeMap(Charset charsetForText) {
        Map<String, MimeType> map = new HashMap<>();
        map.put("html", new Text("text/html", charsetForText));
        map.put("htm", new Text("text/html", charsetForText));
        map.put("txt", new Text("text/plain", charsetForText));
        map.put("css", new Text("text/css", charsetForText));
        map.put("csv", new Text("text/csv", charsetForText));
        map.put("xml", new Text("text/xml", charsetForText));
        map.put("js", new Text("text/javascript", charsetForText));
        map.put("xhtml", new Text("application/xhtml+xml", charsetForText));
        map.put("json", new Text("application/json", charsetForText));
        map.put("pdf", new Binary("application/pdf"));
        map.put("zip", new Binary("application/zip"));
        map.put("tar", new Binary("application/x-tar"));
        map.put("gif", new Binary("image/gif"));
        map.put("jpeg", new Binary("image/jpeg"));
        map.put("jpg", new Binary("image/jpeg"));
        map.put("tiff", new Binary("image/tiff"));
        map.put("tif", new Binary("image/tiff"));
        map.put("png", new Binary("image/png"));
        map.put("svg", new Binary("image/svg+xml"));
        map.put("ico", new Binary("image/vnd.microsoft.icon"));
        return map;
    }


}
