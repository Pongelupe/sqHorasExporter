package com.prosegur.apontamento.service.squadra;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosegur.apontamento.model.LoginInfo;
import com.prosegur.apontamento.model.Timesheet;
import com.prosegur.apontamento.model.TimesheetEntry;

@Service
public class SqHorasService {

	@Value("${app.proxy.host}")
	private String proxyHost;

	@Value("${app.proxy.port}")
	private Integer proxyPort;

	@Value("${app.sqhoras.url}")
	private String sqHorasUrl;

	@Autowired
	private ObjectMapper mapper;

	public Timesheet retrieveTimesheetFromSqHoras(LoginInfo loginSqhoras,
			Date month) throws Exception {
		System.setProperty("https.proxyHost", proxyHost);
		System.setProperty("https.proxyPort", proxyPort.toString());
		System.setProperty("jsse.enableSNIExtension", "false");

		CloseableHttpClient client = createClient();
		login(loginSqhoras, client);
		return getAppointmentsFromMonth(client, month);
	}

	@SuppressWarnings("unchecked")
	private Timesheet getAppointmentsFromMonth(CloseableHttpClient client,
			Date month) {
		Timesheet timesheet = new Timesheet();
		timesheet.setTargetMonth(month);
		timesheet.setMesano(new SimpleDateFormat("MM/yyyy").format(month));

		SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
		final String urlDate = "http://www.squadra.com.br/sqhoras/reguaJson.asp?PROCTYPE=RETORNA_APROPRIACOES_DIA&dataSelecionada=1&existePreF=False&existePgto=False&existeCargaSqcustos=False&estaFaturado=False";

		LocalDate firstOfTheMonth = getFirstDayOfMonth(month);
		LocalDate lastOfTheMonth = getLastDayOfMonth(month).plusDays(1);

		ArrayList<LocalDate> dates = new ArrayList<>();
		for (LocalDate date = firstOfTheMonth; date
				.isBefore(lastOfTheMonth); date = date.plusDays(1)) {
			if (date.isBefore(LocalDate.now())
					|| date.isEqual(LocalDate.now())) {
				dates.add(date);
			}
		}

		dates.parallelStream().forEach(date -> {
			try {
				HttpPost httpPost = new HttpPost(urlDate.replace("1",
						formatter.format(Date.from(date.atStartOfDay()
								.atZone(ZoneId.systemDefault()).toInstant()))));
				httpPost.setHeader("Content-Type",
						"application/json;charset=UTF-8");

				CloseableHttpResponse response = doRequest(client, httpPost);
				InputStream responseContent = response.getEntity().getContent();
				Map<String, Object> jsonAsMap = mapper.readValue(
						IOUtils.toString(responseContent,
								StandardCharsets.UTF_8.name()),
						new TypeReference<Map<String, Object>>() {
						});
				List<Map<String, String>> intervals = ((List<Map<String, String>>) jsonAsMap
						.get("intervalos"));
				List<LocalTime> appointments = intervals.stream()
						.map(interval -> Arrays.asList(
								interval.get("horaInicial"),
								interval.get("horaFinal")))
						.flatMap(Collection::stream)
						.map(hour -> LocalTime.parse(hour,
								DateTimeFormatter.ofPattern("HH:mm")))
						.sorted().collect(Collectors.toList());
				timesheet.addEntry(new TimesheetEntry(date, appointments));

				httpPost.releaseConnection();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		timesheet.getEntries()
				.sort((o1, o2) -> o1.getDate().compareTo(o2.getDate()));
		return timesheet;
	}

	private LocalDate getFirstDayOfMonth(Date month) {
		return month.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
				.withDayOfMonth(1);
	}
	private LocalDate getLastDayOfMonth(Date month) {
		return month.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
				.with(TemporalAdjusters.lastDayOfMonth());
	}

	public void login(LoginInfo loginInfo, CloseableHttpClient client)
			throws Exception {
		HttpGet requestGetLogin = new HttpGet(sqHorasUrl + "default.asp");
		doRequest(client, requestGetLogin);

		List<NameValuePair> params = new ArrayList<>();
		params.add(new BasicNameValuePair("origem", StringUtils.EMPTY));
		params.add(new BasicNameValuePair("acao", "Entrar"));
		params.add(new BasicNameValuePair("usuario", loginInfo.getUsername()));
		params.add(new BasicNameValuePair("senha", loginInfo.getPassword()));
		params.add(new BasicNameValuePair("hidusuario", StringUtils.EMPTY));
		params.add(new BasicNameValuePair("hidp", StringUtils.EMPTY));
		params.add(new BasicNameValuePair("URL_DESTINO", StringUtils.EMPTY));
		requestGetLogin.releaseConnection();

		HttpPost postLogin = new HttpPost(sqHorasUrl + "default.asp");
		HttpEntity entity = new UrlEncodedFormEntity(params);
		postLogin.setEntity(entity);
		doRequest(client, postLogin);
		postLogin.releaseConnection();

		HttpGet welcomeRequest = new HttpGet(
				sqHorasUrl + "bemVindoGeral.asp?URL=");
		doRequest(client, welcomeRequest);
		welcomeRequest.releaseConnection();
	}
	private CloseableHttpClient createClient() throws NoSuchAlgorithmException,
			KeyManagementException, KeyStoreException {
		int timeout = 25;
		RequestConfig config = RequestConfig.custom()
				.setConnectTimeout(timeout * 1000)
				.setConnectionRequestTimeout(timeout * 1000)
				.setSocketTimeout(timeout * 1000).build();
		return HttpClientBuilder.create().setDefaultRequestConfig(config)
				.setSSLSocketFactory(
						new SSLConnectionSocketFactory(
								new SSLContextBuilder()
										.loadTrustMaterial(null,
												new TrustSelfSignedStrategy())
										.build(),
								NoopHostnameVerifier.INSTANCE))
				.setProxy(new HttpHost(proxyHost, proxyPort))
				.setDefaultCookieStore(new BasicCookieStore()).build();
	}

	private CloseableHttpResponse doRequest(CloseableHttpClient client,
			HttpRequestBase request) throws IOException, InterruptedException {
		try {
			return client.execute(request);
		} catch (NoHttpResponseException e) {
			Thread.sleep(3000);
			return doRequest(client, request);
		}
	}
}
