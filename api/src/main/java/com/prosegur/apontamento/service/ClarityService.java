package com.prosegur.apontamento.service;

import com.prosegur.apontamento.ApontamentoApplication;
import com.prosegur.apontamento.model.*;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.script.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.WEEKS;

@Service
public class ClarityService {

    @Value("${app.clarity.url}")
    private String clarityUrl;

    @Value("${app.proxy.host}")
    private String proxyHost;

    @Value("${app.proxy.port}")
    private Integer proxyPort;

    @PostConstruct
    public void afterPropertiesSet() {
        System.setProperty("https.proxyHost", proxyHost);
        System.setProperty("https.proxyPort", proxyPort.toString());
        System.setProperty("jsse.enableSNIExtension", "false");
    }

    public void sync(Timesheet timesheet, LoginInfo loginInfo) throws Exception {
        Assert.notEmpty(timesheet.getEntries(), "Selecione pelo menos uma entrada para o lançamento");
        CloseableHttpClient client = this.getClient();
        BasicResponseHandler responseHandler = new BasicResponseHandler();
        JSONObject authContext = this.authenticate(client, loginInfo);
        Integer resourceId = authContext.getJSONObject("userContext").getInt("resourceId");

        timesheet.getClarityTimesheets().clear();

        YearMonth yearMonth = YearMonth.parse(timesheet.getMesano(), DateTimeFormatter.ofPattern("MM/yyyy"));
        LocalDate initialDate = LocalDate.from(yearMonth.atDay(1));
        JSONArray clarityTimesheets4Month = this.getTimesheets(authContext.getString("authToken"), resourceId, initialDate, client);
        for (int i = 0; i < clarityTimesheets4Month.length(); i++) {
            JSONObject clarityTimesheetJson = clarityTimesheets4Month.getJSONObject(i);
            LocalDateTime start = LocalDateTime.parse(clarityTimesheetJson.getString("start_date"), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            LocalDateTime finish = LocalDateTime.parse(clarityTimesheetJson.getString("finish_date"), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

            ClarityTimesheet clarityTimesheet = ClarityTimesheet.builder()
                    .startDate(start.toLocalDate())
                    .finishDate(finish.toLocalDate())
                    .build();

            if (clarityTimesheetJson.getBoolean("has_entries")) {
                int timesheetId = clarityTimesheetJson.getJSONObject("tpTimesheet").getJSONArray("_results").getJSONObject(0).getInt("timesheet_id");

                clarityTimesheet.setId(timesheetId);

                JSONObject clarityTimesheetDetailJson = this.getTimesheet(authContext.getString("authToken"), timesheetId, client);
                List<ClarityTask> tasks = new ArrayList<>();
                boolean hasEntries = clarityTimesheetDetailJson.getJSONObject("timeentries").has("_results");
                if (hasEntries) {
                    JSONArray clarityTasksJson = clarityTimesheetDetailJson.getJSONObject("timeentries").getJSONArray("_results");

                    tasks = new ArrayList<>();
                    for (int j = 0; j < clarityTasksJson.length(); j++) {
                        JSONObject clarityTaskJson = clarityTasksJson.getJSONObject(j);
                        tasks.add(ClarityTask.builder()
                                .taskId(clarityTaskJson.getInt("taskId"))
                                .taskName(clarityTaskJson.getString("taskName"))
                                .internalId(clarityTaskJson.getInt("_internalId"))
                                .build()
                        );
                    }
                }

                clarityTimesheet.setTasks(tasks);
            }
            timesheet.getClarityTimesheets().add(clarityTimesheet);
        }
    }

    public void execute(Timesheet timesheet, LoginInfo loginInfo) throws Exception {
        Assert.notEmpty(timesheet.getEntries(), "Selecione pelo menos uma entrada para o lançamento");
        CloseableHttpClient client = this.getClient();

        JSONObject authContext = this.authenticate(client, loginInfo);
        timesheet.getEntries()
                .stream()
                .collect(Collectors.groupingBy(TimesheetEntry::getWeekOfMonth, Collectors.toSet()))
                .forEach((weekOfMonth, timesheetEntries) -> {
                    final ClarityTimesheet clarityTimesheet = timesheet.getClarityTimesheets().get(weekOfMonth - 1);

                    Double totalTime = timesheetEntries
                            .stream()
                            .mapToDouble(this::calcHours).sum();

                    timesheetEntries
                            .stream()
                            .collect(Collectors.groupingBy(TimesheetEntry::getClarityTask, Collectors.toSet()))
                            .forEach((clarityTask, entries) -> {
                                Double taskTotalTime = entries
                                        .stream()
                                        .mapToDouble(this::calcHours).sum();

                                JSONArray segments = new JSONArray();
                                for (LocalDate date = clarityTimesheet.getStartDate(); date.isBefore(clarityTimesheet.getFinishDate()); date = date.plusDays(1)) {
                                    final LocalDate dateToFilter = date;
                                    Optional<TimesheetEntry> timesheetEntry = entries.stream()
                                            .filter(entry -> dateToFilter.equals(LocalDate.of(timesheet.getYear(), timesheet.getMonth(), entry.getDay())))
                                            .findFirst();
                                    if (timesheetEntry.isPresent()) {
                                        segments.put(new JSONObject()
                                                .put("start", dateToFilter.atTime(0,0,0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                                                .put("finish", dateToFilter.plusDays(1).atTime(0,0,0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                                                .put("value", Math.round(calcHours(timesheetEntry.get()) * 60 * 60)));
                                    } else {
                                        segments.put(new JSONObject()
                                                .put("start", dateToFilter.atTime(0,0,0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                                                .put("finish", dateToFilter.plusDays(1).atTime(0,0,0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                                                .put("value", 0));
                                    }
                                }

                                JSONObject timeEntries = new JSONObject()
                                        .put("taskId", clarityTask.getTaskId())
                                        .put("actuals", new JSONObject()
                                                .put("isFiscal", false)
                                                .put("curveType", "value")
                                                .put("total", 0)
                                                .put("dataType", "numeric")
                                                .put("_type", "tsv")
                                                .put("workEffortUnit", "none")
                                                .put("start", clarityTimesheet.getStartDate().atTime(0,0,0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                                                .put("finish", clarityTimesheet.getFinishDate().atTime(0,0,0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                                                .put("segmentList", new JSONObject()
                                                        .put("total", 0)
                                                        .put("defaultValue", 0)
                                                        .put("segments", segments)
                                                )
                                        );

                                try {
                                    HttpPut putTimeEntries = new HttpPut(String.format("http://ppm.prosegur.local/ppm/rest/v1/timesheets/%d/timeEntries/%d", clarityTimesheet.getId(), clarityTask.getInternalId()));
                                    putTimeEntries.addHeader("x-api-next-string", generateApiNextString(authContext.getString("authToken")));
                                    putTimeEntries.addHeader("x-api-force-patch", "true");
                                    putTimeEntries.addHeader("x-api-full-response", "true");
                                    putTimeEntries.addHeader("authToken", authContext.getString("authToken"));
                                    putTimeEntries.setEntity(new StringEntity(timeEntries.toString(), ContentType.APPLICATION_JSON));
                                    HttpResponse timeEntriesResponse = client.execute(putTimeEntries);
                                    putTimeEntries.releaseConnection();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                });
    }

    private double calcHours(TimesheetEntry entry) {
        return Math.round(Duration.between(entry.getStart1(), entry.getEnd1())
                .plus(Duration.between(entry.getStart2(), entry.getEnd2())).toMinutes() / 60.0 * 100.0) / 100.0;
    }

    private class B64Encode implements Function<String, String> {
        private Base64.Encoder encoder = Base64.getEncoder();

        public String apply(String src) {
            return encoder.encodeToString(src.getBytes());
        }
    }

    public CloseableHttpClient getClient() throws Exception {
        CloseableHttpClient client = HttpClientBuilder
                .create()
                .setSSLSocketFactory(
                        new SSLConnectionSocketFactory(new SSLContextBuilder()
                                .loadTrustMaterial(null, (chain, authType) -> true)
                                .build(), NoopHostnameVerifier.INSTANCE)
                )
                .setDefaultCookieStore(new BasicCookieStore())
                .build();

        HttpGet get = new HttpGet("http://ppm.prosegur.local/pm/");
        client.execute(get);
        get.releaseConnection();

        get = new HttpGet(clarityUrl + "auth/login?_cb=" + new Date().getTime());
        client.execute(get);
        get.releaseConnection();
        return client;
    }

    public JSONObject authenticate(CloseableHttpClient client, LoginInfo loginInfo) throws Exception {
        HttpPost postLogin = new HttpPost(clarityUrl + "auth/login");
        postLogin.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(String.format("%s:%s", loginInfo.getUsername(), loginInfo.getPassword()).getBytes()));
        postLogin.addHeader("x-api-user-context", "true");
        postLogin.addHeader("x-api-next-string", generateApiNextString(""));
        postLogin.setEntity(new StringEntity("{}", ContentType.APPLICATION_JSON));
        HttpResponse loginResponse = client.execute(postLogin);
        JSONObject response = new JSONObject(new BasicResponseHandler().handleEntity(loginResponse.getEntity()));
        postLogin.releaseConnection();
        return response;
    }

    public JSONArray getTimesheets(String authToken, Integer resourceId, LocalDate initialDate, CloseableHttpClient client) throws Exception {
        String filter = String.format("((resourceId = %d) and (numberOfPeriodsPrev = 0) and (numberOfPeriodsNext = 6) and (selectedDate = '%s'))", resourceId, LocalDateTime.of(initialDate, LocalTime.of(0, 0, 0)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));

        HttpGet getTimesheetFromCarroussel = new HttpGet(clarityUrl + "private/timesheetCarousel?filter=" + URLEncoder.encode(filter, "UTF-8") + "&_cb=" + new Date().getTime());
        getTimesheetFromCarroussel.addHeader("x-api-next-string", generateApiNextString(authToken));
        getTimesheetFromCarroussel.addHeader("authToken", authToken);
        HttpResponse timesheetResponse = client.execute(getTimesheetFromCarroussel);
        JSONObject response = new JSONObject(new BasicResponseHandler().handleEntity(timesheetResponse.getEntity()));
        JSONArray results = response.getJSONObject("tscarousel").getJSONArray("_results");
        JSONArray timesheets = new JSONArray();

        LocalDate finalDate = LocalDate.from(initialDate.plusMonths(1));
        for (int i = 0; i < results.length(); i++) {
            JSONObject timesheet = results.getJSONObject(i);
            LocalDateTime start = LocalDateTime.parse(timesheet.getString("start_date"), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            LocalDateTime finish = LocalDateTime.parse(timesheet.getString("finish_date"), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            if (
                    (start.isAfter(initialDate.atTime(0, 0, 0)) || start.isEqual(initialDate.atTime(0, 0, 0)))
                            && (finish.isBefore(finalDate.atTime(0, 0, 0)) || finish.isEqual(finalDate.atTime(0, 0, 0)))
            ) {
                timesheets.put(timesheet);
            }
        }

        return timesheets;
    }

    public JSONObject getTimesheet(String authToken, Integer timesheetId, CloseableHttpClient client) throws Exception {
        String filter = String.format("(timesheetId = %d)", timesheetId);

        HttpGet getTimesheet = new HttpGet(clarityUrl + "private/timesheet?filter=" + URLEncoder.encode(filter, "UTF-8") + "&_cb=" + new Date().getTime());
        getTimesheet.addHeader("x-api-next-string", generateApiNextString(authToken));
        getTimesheet.addHeader("authToken", authToken);
        HttpResponse timesheetResponse = client.execute(getTimesheet);
        JSONObject response = new JSONObject(new BasicResponseHandler().handleEntity(timesheetResponse.getEntity()));

        return response.getJSONObject("timesheets").getJSONArray("_results").getJSONObject(0);
    }

    public JSONObject getTimesheetApp(String authToken, Integer resourceId, Integer timesheetId, CloseableHttpClient client) throws Exception {
        String filter = String.format("(timesheetId = %d)", timesheetId);

        HttpGet getTimesheet = new HttpGet(clarityUrl + "private/timesheet?" + URLEncoder.encode(filter, "UTF-8") + "&_cb=" + new Date().getTime());
        getTimesheet.addHeader("x-api-next-string", generateApiNextString(authToken));
        getTimesheet.addHeader("authToken", authToken);
        HttpResponse timesheetResponse = client.execute(getTimesheet);
        JSONObject response = new JSONObject(new BasicResponseHandler().handleEntity(timesheetResponse.getEntity()));

        return response.getJSONObject("timesheets").getJSONArray("_results").getJSONObject(0);
    }

    protected String generateApiNextString(String input) throws ScriptException {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("javascript");
        Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        bindings.put("btoa", new B64Encode());

        return (String) engine.eval("" +
                "var t = \"Q2xhcml0eS0xNC40LUFybXN0cm9uZw==\";\n" +
                "var n = \"VGhlIHF1aWNrIGJyb3duIGZveA\";\n" +
                "var a = 12;\n" +
                "var r = 15;\n" +
                "\n" +
                "function generateRandom() {\n" +
                "            var e, t = n.length, a = \"\";\n" +
                "            for (e = 0; t > e; e++)\n" +
                "                a += n.charAt(Math.floor(Math.random() * t));\n" +
                "            return a\n" +
                "        }\n" +
                "\n" +
                "function generateString(n) {\n" +
                "  var o;\n" +
                "  return o = n ? parseInt(n.charAt(a), 16): r,\n" +
                "  btoa(btoa(generateRandom().substring(0, o) + t))\n" +
                "}\n" +
                "\n" +
                "generateString(\"" + input + "\");"
        );

    }


}
