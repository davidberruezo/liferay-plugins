/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.jsonwebserviceclient;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;

import java.net.SocketException;
import java.net.UnknownHostException;

import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Ivica Cardic
 * @author Igor Beslic
 */
public class JSONWebServiceClientImpl implements JSONWebServiceClient {

	public void afterPropertiesSet() {
		HttpClientBuilder httpClientBuilder = HttpClients.custom();

		httpClientBuilder.setConnectionManager(
			getPoolingHttpClientConnectionManager());

		if ((_login != null) && (_password != null)) {
			CredentialsProvider credentialsProvider =
				new BasicCredentialsProvider();

			credentialsProvider.setCredentials(
				new AuthScope(_hostName, _hostPort),
				new UsernamePasswordCredentials(_login, _password));

			httpClientBuilder.setDefaultCredentialsProvider(
				credentialsProvider);
			httpClientBuilder.setRetryHandler(
				new HttpRequestRetryHandlerImpl());
		}
		else {
			if (_logger.isWarnEnabled()) {
				_logger.warn("Login and password are required");
			}
		}

		try {
			_closeableHttpClient = httpClientBuilder.build();

			if (_logger.isDebugEnabled()) {
				_logger.debug(
					"Configured client for " + _protocol + "://" + _hostName);
			}
		}
		catch (Exception e) {
			_logger.error("Unable to configure client", e);
		}
	}

	protected PoolingHttpClientConnectionManager
		getPoolingHttpClientConnectionManager() {

		PoolingHttpClientConnectionManager poolingHttpClientConnectionManager =
			null;

		if (_keyStore != null) {
			poolingHttpClientConnectionManager =
				new PoolingHttpClientConnectionManager(
					getSocketFactoryRegistry(), null, null, null, 60000,
					TimeUnit.MILLISECONDS);
		}
		else {
			poolingHttpClientConnectionManager =
				new PoolingHttpClientConnectionManager(
					60000, TimeUnit.MILLISECONDS);
		}

		poolingHttpClientConnectionManager.setMaxTotal(20);

		return poolingHttpClientConnectionManager;
	}

	public void destroy() {
		try {
			_closeableHttpClient.close();
		}
		catch (IOException e) {
			_logger.error("Unable to close client", e);
		}

		_closeableHttpClient = null;
	}

	@Override
	public String doGet(String url, Map<String, String> parameters)
		throws JSONWebServiceTransportException {

		List<NameValuePair> nameValuePairs = toNameValuePairs(parameters);

		if (!nameValuePairs.isEmpty()) {
			String queryString = URLEncodedUtils.format(nameValuePairs, "utf8");

			url += "?" + queryString;
		}

		if (_logger.isDebugEnabled()) {
			_logger.debug(
				"Sending GET request to " + _login + "@" + _hostName + url);
		}

		HttpGet httpGet = new HttpGet(url);

		for (String key : _headers.keySet()) {
			httpGet.addHeader(key, _headers.get(key));
		}

		return execute(httpGet);
	}

	@Override
	public String doPost(String url, Map<String, String> parameters)
		throws JSONWebServiceTransportException {

		if (_logger.isDebugEnabled()) {
			_logger.debug(
				"Sending POST request to " + _login + "@" + _hostName + url);
		}

		try {
			HttpPost httpPost = new HttpPost(url);

			List<NameValuePair> nameValuePairs = toNameValuePairs(parameters);

			HttpEntity httpEntity = new UrlEncodedFormEntity(
				nameValuePairs, "utf8");

			for (String key : _headers.keySet()) {
				httpPost.addHeader(key, _headers.get(key));
			}

			httpPost.setEntity(httpEntity);

			return execute(httpPost);
		}
		catch (UnsupportedEncodingException uee) {
			throw new JSONWebServiceTransportException.CommunicationFailure(
				uee);
		}
	}

	@Override
	public String doPostAsJSON(String url, String json)
		throws JSONWebServiceTransportException {

		HttpPost httpPost = new HttpPost(url);

		for (String key : _headers.keySet()) {
			httpPost.addHeader(key, _headers.get(key));
		}

		StringEntity stringEntity = new StringEntity(json.toString(), "utf8");

		stringEntity.setContentType("application/json");

		httpPost.setEntity(stringEntity);

		return execute(httpPost);
	}

	public Map<String, String> getHeaders() {
		return _headers;
	}

	public String getHostName() {
		return _hostName;
	}

	public int getPort() {
		return _hostPort;
	}

	public String getProtocol() {
		return _protocol;
	}

	@Override
	public void resetHttpClient() {
		destroy();

		afterPropertiesSet();
	}

	public void setHeaders(Map<String, String> headers) {
		_headers = headers;
	}

	public void setHostName(String hostName) {
		_hostName = hostName;
	}

	public void setHostPort(int hostPort) {
		_hostPort = hostPort;
	}

	@Override
	public void setKeyStore(KeyStore keyStore) {
		_keyStore = keyStore;
	}

	@Override
	public void setLogin(String login) {
		_login = login;
	}

	@Override
	public void setPassword(String password) {
		_password = password;
	}

	public void setProtocol(String protocol) {
		_protocol = protocol;
	}

	protected String execute(HttpRequestBase httpRequestBase)
		throws JSONWebServiceTransportException {

		HttpHost httpHost = new HttpHost(_hostName, _hostPort, _protocol);

		try {
			if (_closeableHttpClient == null) {
				afterPropertiesSet();
			}

			HttpResponse httpResponse = _closeableHttpClient.execute(
				httpHost, httpRequestBase);

			StatusLine statusLine = httpResponse.getStatusLine();

			if (statusLine.getStatusCode() ==
					HttpServletResponse.SC_UNAUTHORIZED) {

				throw new JSONWebServiceTransportException.
					AuthenticationFailure(
					"Not authorized to access JSON web service");
			}
			else if (statusLine.getStatusCode() >= 400) {
				throw new JSONWebServiceTransportException.CommunicationFailure(
					statusLine.getStatusCode());
			}

			return EntityUtils.toString(httpResponse.getEntity(), "utf8");
		}
		catch (IOException ioe) {
			throw new JSONWebServiceTransportException.CommunicationFailure(
				"Unable to transmit request", ioe);
		}
		finally {
			httpRequestBase.releaseConnection();
		}
	}

	protected Registry<ConnectionSocketFactory> getSocketFactoryRegistry() {
		RegistryBuilder<ConnectionSocketFactory> registryBuilder =
			RegistryBuilder.<ConnectionSocketFactory> create();

		registryBuilder.register("http", new PlainConnectionSocketFactory());
		registryBuilder.register("https", getSSLConnectionSocketFactory());

		return registryBuilder.build();
	}

	protected SSLConnectionSocketFactory getSSLConnectionSocketFactory() {
		SSLContextBuilder sslContextBuilder = SSLContexts.custom();

		SSLContext sslContext = null;

		try {
			sslContextBuilder.loadTrustMaterial(_keyStore);

			sslContext = sslContextBuilder.build();

			sslContext.init(
				null, new TrustManager[] {new X509TrustManagerImpl()}, null);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

		return new SSLConnectionSocketFactory(
			sslContext, new String[] {"TLSv1"}, null,
			SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
	}

	protected List<NameValuePair> toNameValuePairs(
		Map<String, String> parameters) {

		List<NameValuePair> nameValuePairs = new LinkedList<NameValuePair>();

		for (Map.Entry<String, String> entry : parameters.entrySet()) {
			String key = entry.getKey();

			String value = entry.getValue();

			if (value == null) {
				key = "-" + key;

				value = "";
			}

			NameValuePair nameValuePair = new BasicNameValuePair(key, value);

			nameValuePairs.add(nameValuePair);
		}

		return nameValuePairs;
	}

	private static Logger _logger = LoggerFactory.getLogger(
		JSONWebServiceClientImpl.class);

	private CloseableHttpClient _closeableHttpClient;
	private Map<String, String> _headers = Collections.emptyMap();
	private String _hostName;
	private int _hostPort = 80;
	private KeyStore _keyStore;
	private String _login;
	private String _password;
	private String _protocol = "http";

	private class HttpRequestRetryHandlerImpl
		implements HttpRequestRetryHandler {

		public boolean retryRequest(
			IOException ioe, int retryCount, HttpContext httpContext) {

			if (retryCount >= 5) {
				return false;
			}

			if (ioe instanceof ConnectTimeoutException) {
				return false;
			}

			if (ioe instanceof InterruptedIOException) {
				return false;
			}

			if (ioe instanceof SocketException) {
				return true;
			}

			if (ioe instanceof SSLException) {
				return false;
			}

			if (ioe instanceof UnknownHostException) {
				return false;
			}

			HttpClientContext httpClientContext = HttpClientContext.adapt(
				httpContext);

			HttpRequest httpRequest = httpClientContext.getRequest();

			if (httpRequest instanceof HttpEntityEnclosingRequest) {
				return false;
			}

			return true;
		}

	};

	private class X509TrustManagerImpl implements X509TrustManager {

		public X509TrustManagerImpl() {
			try {
				TrustManagerFactory trustManagerFactory =
					TrustManagerFactory.getInstance(
						TrustManagerFactory.getDefaultAlgorithm());

				trustManagerFactory.init((KeyStore)null);

				for (TrustManager trustManager :
						trustManagerFactory.getTrustManagers()) {

					if (trustManager instanceof X509TrustManager) {
						_x509TrustManager = (X509TrustManager)trustManager;

						break;
					}
				}
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void checkClientTrusted(
				X509Certificate[] x509Certificates, String authType)
			throws CertificateException {

			if (x509Certificates.length != 1) {
				_x509TrustManager.checkClientTrusted(
					x509Certificates, authType);
			}
		}

		@Override
		public void checkServerTrusted(
				X509Certificate[] x509Certificates, String authType)
			throws CertificateException {

			if (x509Certificates.length != 1) {
				_x509TrustManager.checkServerTrusted(
					x509Certificates, authType);
			}
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return _x509TrustManager.getAcceptedIssuers();
		}

		private X509TrustManager _x509TrustManager;

	}

}