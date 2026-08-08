package com.bg7yoz.ft8cn.log;

import android.util.Log;

import com.bg7yoz.ft8cn.GeneralVariables;

import org.json.JSONStringer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;

enum ServiceType {
    Cloudlog,
    QRZ
}

public class ThirdPartyService {
    public static String TAG = "ThirdPartyService";

    private static final String[] LOGBOOK_QSO_API_PATHS = {
            "index.php/api/qso",
            "api/qso/",
            "api/qso"
    };
    private static final String[] LOGBOOK_VERSION_API_PATHS = {
            "index.php/api/version",
            "api/version/"
    };
    private static final String[] LOGBOOK_STATION_INFO_API_PATHS = {
            "index.php/api/station_info/",
            "api/station_info/"
    };

    private static String QSLRecordToADIF(QSLRecord qslRecord, ServiceType serv) {
        StringBuilder logStr = new StringBuilder();
        logStr.append(String.format(Locale.ROOT, "<call:%d>%s ",
                qslRecord.getToCallsign().length(),
                qslRecord.getToCallsign()));

        if (qslRecord.getToMaidenGrid() != null) {
            logStr.append(String.format(Locale.ROOT, "<gridsquare:%d>%s ",
                    qslRecord.getToMaidenGrid().length(),
                    qslRecord.getToMaidenGrid()));
        }

        if (qslRecord.getMode() != null) {
            logStr.append(String.format(Locale.ROOT, "<mode:%d>%s ",
                    qslRecord.getMode().length(),
                    qslRecord.getMode()));
        }

        logStr.append(String.format(Locale.ROOT, "<rst_sent:%d>%s ",
                String.valueOf(qslRecord.getSendReport()).length(),
                String.valueOf(qslRecord.getSendReport())));
        logStr.append(String.format(Locale.ROOT, "<rst_rcvd:%d>%s ",
                String.valueOf(qslRecord.getReceivedReport()).length(),
                String.valueOf(qslRecord.getReceivedReport())));

        if (qslRecord.getQso_date() != null) {
            logStr.append(String.format(Locale.ROOT, "<qso_date:%d>%s ",
                    qslRecord.getQso_date().length(),
                    qslRecord.getQso_date()));
        }

        if (qslRecord.getTime_on() != null) {
            logStr.append(String.format(Locale.ROOT, "<time_on:%d>%s ",
                    qslRecord.getTime_on().length(),
                    qslRecord.getTime_on()));
        }

        if (qslRecord.getBandLength() != null) {
            logStr.append(String.format(Locale.ROOT, "<band:%d>%s ",
                    qslRecord.getBandLength().length(),
                    qslRecord.getBandLength()));
        }

        if (qslRecord.getQso_date_off() != null) {
            logStr.append(String.format(Locale.ROOT, "<qso_date_off:%d>%s ",
                    qslRecord.getQso_date_off().length(),
                    qslRecord.getQso_date_off()));
        }

        if (qslRecord.getTime_off() != null) {
            logStr.append(String.format(Locale.ROOT, "<time_off:%d>%s ",
                    qslRecord.getTime_off().length(),
                    qslRecord.getTime_off()));
        }

        String freq = "";
        Log.d(TAG, String.valueOf(qslRecord.getBandFreq()));
        if (serv == ServiceType.Cloudlog || serv == ServiceType.QRZ) {
            double i = (double) qslRecord.getBandFreq() / 1000000;
            freq = String.valueOf(i);
        }
        logStr.append(String.format(Locale.ROOT, "<freq:%d>%s ", freq.length(), freq));

        if (qslRecord.getMyCallsign() != null) {
            logStr.append(String.format(Locale.ROOT, "<station_callsign:%d>%s ",
                    qslRecord.getMyCallsign().length(),
                    qslRecord.getMyCallsign()));
        }

        if (qslRecord.getMyMaidenGrid() != null) {
            logStr.append(String.format(Locale.ROOT, "<my_gridsquare:%d>%s ",
                    qslRecord.getMyMaidenGrid().length(),
                    qslRecord.getMyMaidenGrid()));
        }

        String comment = qslRecord.getComment();
        logStr.append(String.format(Locale.ROOT, "<comment:%d>%s <eor>\n",
                comment.length(),
                comment));
        return logStr.toString();
    }

    public static void UploadToCloudLog(QSLRecord qslRecord) {
        String logStr = QSLRecordToADIF(qslRecord, ServiceType.Cloudlog);
        Log.d(TAG, logStr);
        String address = normalizeBaseUrl(GeneralVariables.getCloudlogServerAddress());
        if (address.isEmpty()) return;

        try {
            JSONStringer js = new JSONStringer();
            String result = js.object()
                    .key("key").value(GeneralVariables.getCloudlogServerApiKey())
                    .key("station_profile_id").value(GeneralVariables.getCloudlogStationID())
                    .key("type").value("adif")
                    .key("string").value(logStr)
                    .endObject().toString();

            HttpResult uploadResult = postCloudlogOrWavelog(address, result);
            if (uploadResult.isSuccess()) {
                Log.d(TAG, "Updated to Cloudlog/Wavelog successfully. result:" + uploadResult.body);
            } else {
                Log.d(TAG, "Cloudlog/Wavelog upload failed. code:" + uploadResult.responseCode
                        + " result:" + uploadResult.body);
            }
        } catch (Exception k) {
            Log.d(TAG, k.toString());
        }
    }

    public static boolean CheckCloudlogConnection() {
        String address = normalizeBaseUrl(GeneralVariables.getCloudlogServerAddress());
        String apiKey = GeneralVariables.getCloudlogServerApiKey();
        String stationId = GeneralVariables.getCloudlogStationID();
        if (address.isEmpty()
                || apiKey == null || apiKey.trim().isEmpty()
                || stationId == null || stationId.trim().isEmpty()) {
            return false;
        }

        try {
            JSONStringer js = new JSONStringer();
            String json = js.object().key("key").value(apiKey).endObject().toString();

            HttpResult stationInfo = postFirstNon404(address, LOGBOOK_STATION_INFO_API_PATHS, json);
            Log.d(TAG, "Cloudlog/Wavelog station_info test code:" + stationInfo.responseCode
                    + " body:" + stationInfo.body);
            if (stationInfo.isSuccess()) {
                return responseContainsStationId(stationInfo.body, stationId);
            }

            HttpResult stationInfoByUrl = getStationInfoByUrl(address, apiKey);
            Log.d(TAG, "Cloudlog/Wavelog station_info URL test code:"
                    + stationInfoByUrl.responseCode + " body:" + stationInfoByUrl.body);
            if (stationInfoByUrl.isSuccess()) {
                return responseContainsStationId(stationInfoByUrl.body, stationId);
            }

            HttpResult version = postFirstNon404(address, LOGBOOK_VERSION_API_PATHS, json);
            Log.d(TAG, "Cloudlog/Wavelog version test code:" + version.responseCode
                    + " body:" + version.body);
            return version.isSuccess();
        } catch (Exception e) {
            Log.d(TAG, e.toString());
            return false;
        }
    }

    public static boolean CheckQRZConnection() {
        String apiKey = GeneralVariables.getQrzApiKey();
        try {
            String url = "https://logbook.qrz.com/api?KEY=" + apiKey + "&ACTION=STATUS";
            String result = sendGetRequest(url);
            HashMap<String, String> status = new HashMap<>();
            for (String s : result.split("&")) {
                String[] split = s.split("=");
                if (split.length > 1) {
                    status.put(split[0], split[1]);
                }
            }
            Log.d(TAG, status.toString());
            return status.containsKey("RESULT") && status.get("RESULT").equals("OK");
        } catch (Exception e) {
            Log.d(TAG, e.toString());
            return false;
        }
    }

    public static void UploadToQRZ(QSLRecord qslRecord) {
        String logStr = QSLRecordToADIF(qslRecord, ServiceType.QRZ);
        Log.d(TAG, logStr);
        String apikey = GeneralVariables.getQrzApiKey();
        String url = String.format(Locale.ROOT, "https://logbook.qrz.com/api/KEY=%s&ACTION=INSERT&ADIF=%s",
                apikey, logStr);

        try {
            String result = sendGetRequest(url);
            Log.d(TAG, "Updated to QRZ successfully. result:" + result);
        } catch (Exception k) {
            Log.d(TAG, k.toString());
        }
    }

    public static String sendPostRequest(String url, String json) throws IOException {
        return sendPostRequestWithCode(url, json).body;
    }

    private static HttpResult postCloudlogOrWavelog(String address, String json) throws IOException {
        return postFirstNon404(address, LOGBOOK_QSO_API_PATHS, json);
    }

    private static HttpResult postFirstNon404(String address, String[] paths, String json) throws IOException {
        HttpResult lastResult = null;
        for (String path : paths) {
            String url = address + path;
            Log.d(TAG, "Cloudlog/Wavelog POST URL: " + url);
            HttpResult result = sendPostRequestWithCode(url, json);
            lastResult = result;
            if (result.responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
                return result;
            }
        }
        return lastResult == null ? new HttpResult(-1, "") : lastResult;
    }

    private static String normalizeBaseUrl(String address) {
        if (address == null) return "";
        String baseUrl = address.trim();
        if (baseUrl.isEmpty()) return "";
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl;
    }

    static boolean responseContainsStationId(String body, String stationId) {
        if (body == null || stationId == null) return false;
        String normalizedBody = body.replaceAll("\\s+", "");
        String normalizedStationId = stationId.trim();
        if (normalizedStationId.isEmpty()) return false;
        return containsJsonId(normalizedBody, "station_id", normalizedStationId)
                || containsJsonId(normalizedBody, "station_profile_id", normalizedStationId);
    }

    private static boolean containsJsonId(String body, String field, String value) {
        String fieldPrefix = "\"" + field + "\":";
        if (body.contains(fieldPrefix + "\"" + value + "\"")) {
            return true;
        }
        if (!value.matches("[0-9]+")) {
            return false;
        }

        String numericToken = fieldPrefix + value;
        int searchFrom = 0;
        while (true) {
            int tokenStart = body.indexOf(numericToken, searchFrom);
            if (tokenStart < 0) {
                return false;
            }
            int tokenEnd = tokenStart + numericToken.length();
            if (tokenEnd == body.length()
                    || body.charAt(tokenEnd) == ','
                    || body.charAt(tokenEnd) == '}'
                    || body.charAt(tokenEnd) == ']') {
                return true;
            }
            searchFrom = tokenStart + 1;
        }
    }

    private static HttpResult getStationInfoByUrl(String address, String apiKey) throws IOException {
        String encodedApiKey = URLEncoder.encode(apiKey, "UTF-8");
        HttpResult lastResult = null;
        for (String path : LOGBOOK_STATION_INFO_API_PATHS) {
            String url = address + path + encodedApiKey;
            Log.d(TAG, "Cloudlog/Wavelog GET URL: " + url);
            HttpResult result = sendGetRequestWithCode(url);
            lastResult = result;
            if (result.responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
                return result;
            }
        }
        return lastResult == null ? new HttpResult(-1, "") : lastResult;
    }

    private static HttpResult sendPostRequestWithCode(String url, String json) throws IOException {
        HttpURLConnection conn = null;
        BufferedReader reader = null;

        try {
            URL urlObj = new URL(url);
            conn = (HttpURLConnection) urlObj.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            os.write(json.getBytes("UTF-8"));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            } else {
                InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    reader = new BufferedReader(new InputStreamReader(errorStream));
                }
            }

            StringBuilder response = new StringBuilder();
            if (reader != null) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            return new HttpResult(responseCode, response.toString());
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static String sendGetRequest(String url) throws IOException {
        return sendGetRequestWithCode(url).body;
    }

    private static HttpResult sendGetRequestWithCode(String url) throws IOException {
        HttpURLConnection conn = null;
        BufferedReader reader = null;

        try {
            URL urlObj = new URL(url);
            conn = (HttpURLConnection) urlObj.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return new HttpResult(responseCode, response.toString());
            }
            return new HttpResult(responseCode, "");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            if (reader != null) {
                reader.close();
            }
        }
    }

    private static class HttpResult {
        final int responseCode;
        final String body;

        HttpResult(int responseCode, String body) {
            this.responseCode = responseCode;
            this.body = body == null ? "" : body;
        }

        boolean isSuccess() {
            return responseCode >= 200 && responseCode < 300;
        }
    }
}
