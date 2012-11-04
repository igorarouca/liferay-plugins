/**
 * Copyright (c) 2000-2012 Liferay, Inc. All rights reserved.
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

package com.liferay.portal.search.solr.server;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import java.io.IOException;

import java.net.MalformedURLException;

import  org.apache.http.client.HttpClient;
//import org.apache.http.client.HttpConnectionManager;
//import org.apache.http.client.HttpState;
//import org.apache.http.client.MultiThreadedHttpConnectionManager;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.AuthScope;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;

/**
 * @author Bruno Farache
 * @author Israel Olalla
 */
public class BasicAuthSolrServer extends StoppableSolrServer {

	public BasicAuthSolrServer(
			AuthScope authScope, String username, String password, String url)
		throws MalformedURLException {

		_username = username;
		_password = password;
		
		_multiThreadedHttpConnectionManager =
				new ThreadSafeClientConnManager();

			HttpClient httpClient = new DefaultHttpClient(
				_multiThreadedHttpConnectionManager);
		

		if (_username != null && _password != null) {
			if (authScope == null) {
				authScope = AuthScope.ANY;
			}

			// HttpState httpState = httpClient.getState();
		
			((AbstractHttpClient) httpClient).getCredentialsProvider().setCredentials(
				authScope,
				new UsernamePasswordCredentials(_username, _password));
				
		}
		_server = new HttpSolrServer(url, httpClient);
	}

	public BasicAuthSolrServer(String url) throws MalformedURLException {
		this(null, null, url);
	}

	public BasicAuthSolrServer(String username, String password, String url)
		throws MalformedURLException {

		this(null, username, password, url);
	}

	public String getBaseURL() {
		return _server.getBaseURL();
	}

	public HttpClient getHttpClient() {
		return _server.getHttpClient();
	}

	public ModifiableSolrParams getInvariantParams() {
		return _server.getInvariantParams();
	}

	public ResponseParser getParser() {
		return _server.getParser();
	}

	@Override
	public NamedList<Object> request(SolrRequest solrRequest)
		throws IOException, SolrServerException {

		if (_stopped) {
			return null;
		}

		return _server.request(solrRequest);
	}

	public NamedList<Object> request(
			SolrRequest solrRequest, ResponseParser responseParser)
		throws IOException, SolrServerException {

		if (_stopped) {
			return null;
		}

		return _server.request(solrRequest, responseParser);
	}

	public void setAllowCompression(boolean compression) {
		_server.setAllowCompression(compression);
	}

	public void setBaseURL(String baseURL) {
		_server.setBaseURL(baseURL);
	}

	/**
	 * @deprecated
	 */
	public void setConnectionManagerTimeout(int connectionManagerTimeout) {
		_server.setConnectionTimeout(connectionManagerTimeout);
	}

	public void setConnectionTimeout(int connectionTimeout) {
		_server.setConnectionTimeout(connectionTimeout);
	}

	public void setDefaultMaxConnectionsPerHost(int maxConnectionsPerHost) {
		_server.setDefaultMaxConnectionsPerHost(maxConnectionsPerHost);
	}

	public void setFollowRedirects(boolean followRedirects) {
		_server.setFollowRedirects(followRedirects);
	}

	public void setMaxRetries(int maxRetries) {
		_server.setMaxRetries(maxRetries);
	}

	public void setMaxTotalConnections(int maxTotalConnections) {
		_server.setMaxTotalConnections(maxTotalConnections);
	}

	public void setParser(ResponseParser responseParser) {
		_server.setParser(responseParser);
	}

	public void setSoTimeout(int soTimeout) {
		_server.setSoTimeout(soTimeout);
	}

	@Override
	public void stop() {
		_stopped = true;

		while (true) {
			int connectionsInPool =
				_multiThreadedHttpConnectionManager.getConnectionsInPool();

			if (connectionsInPool <= 0) {
				break;
			}

			if (_log.isDebugEnabled()) {
				_log.debug(
					toString() + " waiting on " + connectionsInPool +
						" connections");
			}

			_multiThreadedHttpConnectionManager.closeIdleConnections(200L, java.util.concurrent.TimeUnit.MILLISECONDS);

			try {
				Thread.sleep(200);
			}
			catch (InterruptedException ie) {
			}
		}

		_multiThreadedHttpConnectionManager.shutdown();

		if (_log.isDebugEnabled()) {
			_log.debug(toString() + " is shutdown");
		}
	}

	private static Log _log = LogFactoryUtil.getLog(BasicAuthSolrServer.class);

	private ThreadSafeClientConnManager _multiThreadedHttpConnectionManager;
	private String _password;
	private HttpSolrServer _server;
	private boolean _stopped;
	private String _username;
	
	public void shutdown() {
		// TODO Auto-generated method stub
		
	}
}