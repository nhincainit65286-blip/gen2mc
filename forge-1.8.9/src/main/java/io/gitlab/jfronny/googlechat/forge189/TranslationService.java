package io.gitlab.jfronny.googlechat.forge189;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class TranslationService {
    private TranslationService() {
    }

    static String translate(String text, String sourceLang, String targetLang, int timeoutMs) throws Exception {
        String sl = normalizeLang(sourceLang);
        String tl = normalizeLang(targetLang);
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.name());
        String endpoint = "https://translate.googleapis.com/translate_a/single?client=gtx&sl="
                + URLEncoder.encode(sl, StandardCharsets.UTF_8.name())
                + "&tl=" + URLEncoder.encode(tl, StandardCharsets.UTF_8.name())
                + "&dt=t&q=" + encoded;

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Accept-Charset", "UTF-8");

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Translate HTTP error: " + code);
        }

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }

        return parseTranslatedText(body.toString());
    }

    private static String parseTranslatedText(String json) {
        JsonElement root = new JsonParser().parse(json);
        JsonArray rootArray = root.getAsJsonArray();
        JsonArray sentences = rootArray.get(0).getAsJsonArray();
        StringBuilder out = new StringBuilder();
        for (JsonElement part : sentences) {
            JsonArray sentence = part.getAsJsonArray();
            if (sentence.size() > 0 && !sentence.get(0).isJsonNull()) {
                out.append(sentence.get(0).getAsString());
            }
        }
        return out.toString();
    }

    private static String normalizeLang(String value) {
        if (value == null || value.trim().isEmpty()) return "auto";
        return value.trim();
    }
}
