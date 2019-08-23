package com.prosegur.apontamento.service.squadra;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
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
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.logging.log4j.util.Strings;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.prosegur.apontamento.TimesheetResource.PortalRMRequest;
import com.prosegur.apontamento.TimesheetResource.PortalRMRequest.Entry;
import com.prosegur.apontamento.model.LoginInfo;
import com.prosegur.apontamento.model.RMPortalRequestEntry;
import com.prosegur.apontamento.model.Timesheet;

@Service
public class RMPortalService {

	private static final String VAR_URL_ANEXO = "var urlAnexo =";

	@Value("${app.proxy.host}")
	private String proxyHost;

	@Value("${app.proxy.port}")
	private Integer proxyPort;

	@Value("${app.rmportal.url}")
	private String rmPortalUrl;

	public void execute(PortalRMRequest request) throws Exception {
		System.setProperty("https.proxyHost", proxyHost);
		System.setProperty("https.proxyPort", proxyPort.toString());
		System.setProperty("jsse.enableSNIExtension", "false");

		CloseableHttpClient client = createClient();
		login(request.getLoginInfo(), client);
		String urlAnexo = getUrlAnexo(client);
		HttpGet anexoGet = new HttpGet(urlAnexo);
		CloseableHttpResponse response = doRequest(client, anexoGet);
		Document document = Jsoup.parse(
				new BasicResponseHandler().handleEntity(response.getEntity()));
		HttpPost postAppointment = createAppointmentRequest(request, document);
		doRequest(client, postAppointment);
	}

	public void appointFromTimesheet(Timesheet timesheet,
			LoginInfo loginInfoRMPortal) {
		try {
			PortalRMRequest portalRMRequest = new PortalRMRequest();
			portalRMRequest.setLoginInfo(loginInfoRMPortal);
			portalRMRequest.setEntries(getEntriesFromTimesheet(timesheet));
			execute(portalRMRequest);
		} catch (Exception e) {
			// Ignore
		}

	}

	private List<Entry> getEntriesFromTimesheet(Timesheet timesheet) {
		final SimpleDateFormat formatterDate = new SimpleDateFormat(
				"dd/MM/yyyy");

		return timesheet.getEntries().stream().map(e -> new Entry(
				formatterDate.format(Date.from(e.getDate().atStartOfDay()
						.atZone(ZoneId.systemDefault()).toInstant())),
				Arrays.asList(getCellValue(e.getStart1()),
						getCellValue(e.getEnd1()), getCellValue(e.getStart2()),
						getCellValue(e.getEnd2()))))
				.collect(Collectors.toList());
	}

	private String getCellValue(LocalTime time) {
		return Optional.ofNullable(time)
				.map(t -> t.format(DateTimeFormatter.ofPattern("HH:mm")))
				.orElse(StringUtils.EMPTY);
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

	private HttpPost createAppointmentRequest(PortalRMRequest request,
			Document document) throws UnsupportedEncodingException {
		List<NameValuePair> params = prepareParametersAppointment(request,
				document).stream().map(RMPortalRequestEntry::getParams)
						.flatMap(Collection::stream)
						.collect(Collectors.toList());
		params.add(new BasicNameValuePair("TabSheetHint$SelectedIndex", "0"));
		params.add(new BasicNameValuePair("GB$txtJustificativa",
				"Dia de trabalho"));
		prepareCommomParameters(params, document);

		HttpPost postAppointment = new HttpPost(
				getUrlPostFormAppointment(document));
		HttpEntity entity = new UrlEncodedFormEntity(params);
		postAppointment.setEntity(entity);

		return postAppointment;
	}

	// So sorry
	private String getUrlPostFormAppointment(Document document) {
		return rmPortalUrl + "Corpore.Net"
				+ ((FormElement) document.getElementById("Form1"))
						.attr("action").substring(1).replaceAll("%u", "´");
	}

	private List<RMPortalRequestEntry> prepareParametersAppointment(
			PortalRMRequest request, Document document) {
		Elements lines = document.getElementsByClass("st1");
		List<RMPortalRequestEntry> entries = new ArrayList<>();
		for (Element line : lines) {
			RMPortalRequestEntry entry = new RMPortalRequestEntry();
			Elements tds = line.getElementsByTag("tbody").get(0)
					.getElementsByTag("td");
			tds.stream().map(td -> td.getElementsByTag("span"))
					.filter(spans -> !spans.isEmpty()
							&& spans.get(0).id().endsWith("lblData"))
					.findAny().ifPresent(
							dataSpan -> entry.setDay(dataSpan.get(0).html()));

			List<NameValuePair> paramsLine = tds.stream()
					.map(td -> td.getElementsByTag("input"))
					.filter(e -> !e.isEmpty()).flatMap(Collection::stream)
					.map(input -> new BasicNameValuePair(input.attr("name"),
							StringUtils.defaultIfBlank(input.val(),
									getAppointmentByDayAndIndex(
											request.getEntries(),
											entry.getDay(), input.id())))

					).collect(Collectors.toList());
			entry.setParams(paramsLine);

			entries.add(entry);
		}

		return entries;
	}

	private String getAppointmentByDayAndIndex(List<Entry> entries, String day,
			String id) {
		try {
			int index = getIndexByid(id);
			return index == -1
					? ""
					: getAppointmentsByDay(entries, day).get(index);
		} catch (IndexOutOfBoundsException | NumberFormatException e) {
			return "";
		}
	}

	private int getIndexByid(String id) {
		int index = -1;
		int indexAppointment = Integer
				.parseInt(id.charAt(id.length() - 1) + "");

		if (indexAppointment <= 2) {
			if (id.contains("txtEnt")) {
				index = indexAppointment == 1 ? 0 : 2;
			} else if (id.contains("txtSai")) {
				index = indexAppointment == 1 ? 1 : 3;
			}
		}

		return index;
	}

	private List<String> getAppointmentsByDay(List<Entry> entries, String day) {
		if (CollectionUtils.isEmpty(entries)) {
			return new ArrayList<>();
		} else {
			return entries.stream()
					.filter(e -> e.getDate().equalsIgnoreCase(day)).findAny()
					.map(Entry::getAppointments)
					.orElseGet(ArrayList<String>::new);
		}
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

	private String getUrlAnexo(CloseableHttpClient client)
			throws KeyManagementException, NoSuchAlgorithmException,
			KeyStoreException, IOException, InterruptedException {
		HttpGet get = new HttpGet(rmPortalUrl
				+ "Corpore.Net/Main.aspx?ActionID=PtoEspCartaoActionWeb&SelectedMenuIDKey=btnEspelhoCartao");
		CloseableHttpResponse response = doRequest(client, get);
		Document document = Jsoup.parse(
				new BasicResponseHandler().handleEntity(response.getEntity()));
		return document.getElementsByTag("script").stream().map(Element::data)
				.filter(script -> script.startsWith(VAR_URL_ANEXO)).findFirst()
				.map(script -> rmPortalUrl.concat(script.split(VAR_URL_ANEXO)[1]
						.replaceAll("'", "").replace(';', ' ').trim()
						.substring(1)
						.replace("_ChroActionExec", "PtoatFunActionWeb")))
				.orElseThrow(RuntimeException::new);
	}

	public void login(LoginInfo loginInfo, CloseableHttpClient client)
			throws Exception {
		HttpGet requestGetLogin = new HttpGet(
				rmPortalUrl + "Corpore.Net/Login.aspx?autoload=false");
		CloseableHttpResponse firstGetResponse = doRequest(client,
				requestGetLogin);
		List<NameValuePair> params = prepareParamsLogin(firstGetResponse,
				loginInfo);
		requestGetLogin.releaseConnection();

		HttpPost postLogin = new HttpPost(
				rmPortalUrl + "Corpore.Net/Login.aspx?autoload=false");
		HttpEntity entity = new UrlEncodedFormEntity(params);
		postLogin.setEntity(entity);
		doRequest(client, postLogin);
		postLogin.releaseConnection();
	}

	private List<NameValuePair> prepareParamsLogin(
			CloseableHttpResponse response, LoginInfo loginInfo)
			throws IOException {
		List<NameValuePair> params = new ArrayList<>();
		params.add(new BasicNameValuePair("btnLogin", "Acessar"));
		params.add(new BasicNameValuePair("ddlAlias", "CorporeRM"));
		params.add(new BasicNameValuePair("txtUser", loginInfo.getUsername()));
		params.add(new BasicNameValuePair("txtPass", loginInfo.getPassword()));
		Document document = Jsoup.parse(
				new BasicResponseHandler().handleEntity(response.getEntity()));
		prepareCommomParameters(params, document);
		return params;
	}

	private void prepareCommomParameters(List<NameValuePair> params,
			Document document) {
		params.add(getParamFromHtml(document, "__EVENTTARGET", "GB$btnSalvar"));
		params.add(getParamFromHtml(document, "__EVENTARGUMENT"));
		params.add(getParamFromHtml(document, "__LASTFOCUS"));
		params.add(getParamFromHtml(document, "__VIEWSTATE"));
		params.add(getParamFromHtml(document, "__VIEWSTATEGENERATOR"));
		params.add(getParamFromHtml(document, "__EVENTVALIDATION"));
	}

	private BasicNameValuePair getParamFromHtml(Document document,
			String parameter, String defaultVal) {
		return Optional.ofNullable(document.getElementById(parameter))
				.map(p -> new BasicNameValuePair(parameter, p.val()))
				.orElseGet(() -> new BasicNameValuePair(parameter, defaultVal));
	}

	private BasicNameValuePair getParamFromHtml(Document document,
			String parameter) {
		return getParamFromHtml(document, parameter, Strings.EMPTY);
	}

}
