/**
 * Copyright (c) 2012 Israel Olalla
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

import java.io.IOException;
import java.net.MalformedURLException;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.common.util.NamedList;

/**
 * @author Israel Olalla iolalla@gmail.com
 */
@SuppressWarnings("serial")
public class BasicCloudSolrServer extends SolrServer {
	
	private CloudSolrServer _server;
	
	public BasicCloudSolrServer(String url, String collection) throws MalformedURLException {
		_server = new CloudSolrServer(url);
		_server.setDefaultCollection(collection);
	}


	public NamedList<Object> request(SolrRequest solrRequest)
		throws IOException, SolrServerException {
			return _server.request(solrRequest);
	}

	public NamedList<Object> request(
			SolrRequest solrRequest, ResponseParser responseParser)
		throws IOException, SolrServerException {
		return request(solrRequest);
	}
	
	@Override
	public void shutdown() {
		// TODO Auto-generated method stub
		_server.shutdown();
	}

}