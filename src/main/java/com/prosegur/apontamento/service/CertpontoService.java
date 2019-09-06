package com.prosegur.apontamento.service;

import com.prosegur.apontamento.model.LoginInfo;
import com.prosegur.apontamento.model.Timesheet;
import com.prosegur.apontamento.model.TimesheetEntry;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
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
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CertpontoService {

    @Value("${app.certponto.url}")
    private String certpontoUrl;

    @Value("${app.certponto.sso.url}")
    private String certpontoSSOUrl;

    @Value("${app.proxy.host}")
    private String proxyHost;

    @Value("${app.proxy.port}")
    private Integer proxyPort;

    public void execute(LocalDate start, LocalDate end, LoginInfo loginInfo) throws Exception {
        System.setProperty("https.proxyHost", proxyHost);
        System.setProperty("https.proxyPort", proxyPort.toString());
        System.setProperty("jsse.enableSNIExtension", "false");

        BasicCookieStore cookieStore = new BasicCookieStore();
        CloseableHttpClient client = HttpClientBuilder
                .create()
                .setSSLSocketFactory(
                        new SSLConnectionSocketFactory(new SSLContextBuilder()
                                .loadTrustMaterial(null, (chain, authType) -> true)
                                .build(), NoopHostnameVerifier.INSTANCE)
                )
                .setDefaultCookieStore(cookieStore)
                .setProxy(new HttpHost(proxyHost, proxyPort))
                .build();


        CloseableHttpResponse response = client.execute(new HttpGet(certpontoUrl + "SignIn/ExternalLogin"));
        BasicResponseHandler responseHandler = new BasicResponseHandler();
        Document doc = Jsoup.parse(responseHandler.handleEntity(response.getEntity()));
        String token = doc.body().getElementById("token").val();

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("showforgotpassword", "false"));
        params.add(new BasicNameValuePair("documenttypes", "CPF"));
        params.add(new BasicNameValuePair("authtype", "document"));
        params.add(new BasicNameValuePair("token", StringEscapeUtils.escapeHtml4(token)));

        String url = String.join("?", certpontoSSOUrl, URLEncodedUtils.format(params, "UTF-8"));
        client.execute(new HttpGet(url));

        HttpPost postSignin = new HttpPost(certpontoSSOUrl + "/signin");
        params = new ArrayList<>();
        params.add(new BasicNameValuePair("document", loginInfo.getUsername()));
        params.add(new BasicNameValuePair("password", loginInfo.getPassword()));
        params.add(new BasicNameValuePair("token", token));
        postSignin.setEntity(new UrlEncodedFormEntity(params));
        JSONObject signinJsonObject = new JSONObject(responseHandler.handleEntity(client.execute(postSignin).getEntity()));
        String redirectUrl = signinJsonObject.getString("RedirectUrl");
        postSignin.releaseConnection();

        CloseableHttpClient noRedirectClient = HttpClientBuilder
                .create()
                .setSSLSocketFactory(
                        new SSLConnectionSocketFactory(new SSLContextBuilder()
                                .loadTrustMaterial(null, (chain, authType) -> true)
                                .build(), NoopHostnameVerifier.INSTANCE)
                )
                .disableRedirectHandling()
                .setProxy(new HttpHost(proxyHost, proxyPort))
                .build();

        HttpGet ssoIdGet = new HttpGet(redirectUrl);
        CloseableHttpResponse ssoIdResponse = noRedirectClient.execute(ssoIdGet);
        String ssoId = ssoIdResponse.getFirstHeader("location").getValue().substring(ssoIdResponse.getFirstHeader("location").getValue().indexOf("ssoId=") + 6);

        ssoIdGet.releaseConnection();

        HttpPost authTokenPost = new HttpPost(certpontoUrl + "token");
        authTokenPost.setEntity(new StringEntity("grant_type=password&username=" + URLEncoder.encode(ssoId, "UTF-8") + "&password=26101", Charset.defaultCharset()));
        CloseableHttpResponse authTokenResponse = client.execute(authTokenPost);
        JSONObject loginInfoJson = new JSONObject(responseHandler.handleEntity(authTokenResponse.getEntity()));
        authTokenPost.releaseConnection();

        client.execute(new HttpGet(redirectUrl));


        for (LocalDate date = start; date.isBefore(end.plusDays(1)); date = date.plusDays(1)) {
            try {
                if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    continue;
                }
                params = new ArrayList<>();
                params.add(new BasicNameValuePair("startDate", date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
                params.add(new BasicNameValuePair("endDate",  date.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
                params.add(new BasicNameValuePair("statusApproval", "0"));
                params.add(new BasicNameValuePair("optionSelectForPeriod", "7"));
                params.add(new BasicNameValuePair("isOnlyAbsencesMarkings", ""));
                url  = String.join("?" ,String.format("%s%s", certpontoUrl, "api/employee/individualmarkings"), URLEncodedUtils.format(params, "UTF-8"));

                HttpGet get = new HttpGet(url);
                get.addHeader("authorization", "Bearer " + loginInfoJson.getString("access_token"));
                CloseableHttpResponse execute1 = client.execute(get);
                JSONObject tabelaApontamento = new JSONObject(responseHandler.handleEntity(execute1.getEntity()));
                String idTrabalhador = tabelaApontamento
                        .getJSONArray("Object")
                        .getJSONObject(0)
                        .getJSONArray("ColumnValues")
                        .getJSONObject(4)
                        .getString("Value");
                apontar(getApontamento(date, idTrabalhador, LocalTime.of(8,0)), client, loginInfoJson);
                apontar(getApontamento(date, idTrabalhador, LocalTime.of(17,0)), client, loginInfoJson);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
    }

    private void apontar(JSONObject apontamento, CloseableHttpClient client,JSONObject loginInfoJson) throws IOException {
        HttpPost postApontamento = new HttpPost(certpontoUrl + "api/treatedmarking/post");
        postApontamento.addHeader("authorization", "Bearer " + loginInfoJson.getString("access_token"));
        postApontamento.setEntity(new StringEntity(apontamento.toString(), ContentType.APPLICATION_JSON));
        CloseableHttpResponse execute = client.execute(postApontamento);
        postApontamento.releaseConnection();
    }

    private JSONObject getApontamento(LocalDate date, String idTrabalhador, LocalTime entry) {
        return new JSONObject()
                            .put("TMC_TRB_IDENTI", idTrabalhador)
                            .put("TMC_DTAHOR", entry.atDate(date).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                            .put("TMC_MOTIVO", "")
                            .put("TMC_TJM_IDENTI", 201)
                            .put("TMC_GRUDAT", date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd 00:00")));
    }


}
