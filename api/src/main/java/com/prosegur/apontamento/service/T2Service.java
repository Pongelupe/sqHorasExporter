package com.prosegur.apontamento.service;

import com.prosegur.apontamento.model.LoginInfo;
import com.prosegur.apontamento.model.Timesheet;
import com.prosegur.apontamento.model.TimesheetEntry;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class T2Service {

    @Value("${app.t2.url}")
    private String t2Url;

    @Value("${app.proxy.host}")
    private String proxyHost;

    @Value("${app.proxy.port}")
    private Integer proxyPort;

    public void execute(Timesheet timesheet, LoginInfo loginInfo) throws Exception {
        Assert.notEmpty(timesheet.getEntries(), "Selecione pelo menos uma entrada para o lançamento");
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

        client.execute(new HttpGet(t2Url));

        HttpPost postLogin = new HttpPost(t2Url + "controller.php");
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("form_action", "login"));
        params.add(new BasicNameValuePair("usuario", loginInfo.getUsername()));
        params.add(new BasicNameValuePair("senha", loginInfo.getPassword()));
        HttpEntity entity = new UrlEncodedFormEntity(params);
        postLogin.setEntity(entity);
        client.execute(postLogin);
        postLogin.releaseConnection();

        timesheet.getEntries()
                .forEach(entry -> {
                    try {
                        String dataLancamento = LocalDate.of(timesheet.getYear(), timesheet.getMonth(), entry.getDay()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

                        List<NameValuePair> paramsLancamento = new ArrayList<>();
                        paramsLancamento.add(new BasicNameValuePair("painel", "pessoal"));
                        paramsLancamento.add(new BasicNameValuePair("action", "scd_criar_apontamento_alocado"));
                        paramsLancamento.add(new BasicNameValuePair("id_usuario", ""));
                        paramsLancamento.add(new BasicNameValuePair("data_apontamento", dataLancamento));
                        String url = String.join("?", t2Url, URLEncodedUtils.format(paramsLancamento, "UTF8"));

                        CloseableHttpResponse apontamentoPage = client.execute(new HttpGet(url));

                        BasicResponseHandler responseHandler = new BasicResponseHandler();
                        Document doc = Jsoup.parse(responseHandler.handleEntity(apontamentoPage.getEntity()));
                            String idApontamento = doc.body().getElementById("id_apontamento").val();

                        apontar(entry.getStart1(), entry.getEnd1(), dataLancamento, idApontamento, client);
                        apontar(entry.getStart2(), entry.getEnd2(), dataLancamento, idApontamento, client);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        client.close();
    }

    protected void apontar(LocalTime start, LocalTime end, String dataLancamento, String idApontamento, CloseableHttpClient client) throws IOException {
        HttpPost postApontamento = new HttpPost(t2Url + "controller.php");
        List<NameValuePair> apontamentoParams = new ArrayList<>();
        apontamentoParams.add(new BasicNameValuePair("form_action", "scd_criar_apontamento_alocado"));
        apontamentoParams.add(new BasicNameValuePair("data_apontamento", dataLancamento));
        apontamentoParams.add(new BasicNameValuePair("apontamento", idApontamento));
        apontamentoParams.add(new BasicNameValuePair("inicio", start.toString()));
        apontamentoParams.add(new BasicNameValuePair("termino", end.toString()));
        apontamentoParams.add(new BasicNameValuePair("observacoes_detalhes", ""));
        postApontamento.setEntity(new UrlEncodedFormEntity(apontamentoParams));
        client.execute(postApontamento);
        postApontamento.releaseConnection();
    }

    protected boolean isWeekend(Timesheet timesheet, TimesheetEntry entry) {
        LocalDate date = LocalDate.of(timesheet.getYear(), timesheet.getMonth(), entry.getDay());
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

}
