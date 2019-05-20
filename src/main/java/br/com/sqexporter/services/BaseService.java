package br.com.sqexporter.services;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.sqexporter.exceptions.RequestException;
import br.com.sqexporter.services.impl.PropertyService;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BaseService {

	protected OkHttpClient client;
	protected ObjectMapper mapper = new ObjectMapper();
	protected IPropertyService propertyService;

	public BaseService() {
		client = new OkHttpClient();
		propertyService = new PropertyService();
	}

	protected <T> T doRequest(Request request, Class<T> reponseBody)
			throws RequestException {
		Call call = client.newCall(request);
		Response response = null;
		try {
			response = call.execute();
			if (response.isSuccessful()) {
				return mapper.readValue(response.body().bytes(), reponseBody);
			} else {
				throw new RequestException(request, response);
			}
		} catch (IOException e) {
			throw new RequestException(request, response);
		}
	}

	/**
	 * This interceptor sets the auth token onto the request and sets a 15 secs
	 * read timeout. The timeout was intended for high delayed operations
	 * 
	 * 
	 * @param auth
	 */
	protected void setTokenOnRequestHeader(Auth auth) {
		client = client.newBuilder().readTimeout(60, TimeUnit.SECONDS)
				.addInterceptor(chain -> {
					Request original = chain.request();

					Request request = original.newBuilder()
							.header("User-Agent", "PUCMINAS")
							.header("Authorization",
									auth.getTokenType() + " "
											+ auth.getTokenAccess())
							.method(original.method(), original.body()).build();

					return chain.proceed(request);
				}).build();
	}
}