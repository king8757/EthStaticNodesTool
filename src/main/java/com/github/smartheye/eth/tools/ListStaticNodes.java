package com.github.smartheye.eth.tools;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

public class ListStaticNodes {

	private static final int PAGE_ITEM_SIZE = 100;

	private static PoolingHttpClientConnectionManager pool;

	private static RequestConfig requestConfig;

	private static PingEthNode pingEthNode = new PingEthNode();

	static {

		try {
			// System.out.println("初始化HttpClientTest~~~开始");
			SSLContextBuilder builder = new SSLContextBuilder();
			builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
			SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
			// 配置同时支持 HTTP 和 HTPPS
			Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
					.register("http", PlainConnectionSocketFactory.getSocketFactory()).register("https", sslsf).build();
			// 初始化连接管理器
			pool = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
			// 将最大连接数增加到200，实际项目最好从配置文件中读取这个值
			pool.setMaxTotal(20);
			// 设置最大路由
			pool.setDefaultMaxPerRoute(2);
			// 根据默认超时限制初始化requestConfig
			int socketTimeout = 10000;
			int connectTimeout = 10000;
			int connectionRequestTimeout = 10000;
			requestConfig = RequestConfig.custom().setConnectionRequestTimeout(connectionRequestTimeout)
					.setSocketTimeout(socketTimeout).setConnectTimeout(connectTimeout).build();

			// System.out.println("初始化HttpClientTest~~~结束");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyStoreException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
		}

		// 设置请求超时时间
		requestConfig = RequestConfig.custom().setSocketTimeout(50000).setConnectTimeout(50000)
				.setConnectionRequestTimeout(50000).build();
	}

	public static String getNetworkName(String networkId) {
		if ("1".equals(networkId)) {
			return "MainNet";
		} else if ("4".equals(networkId)) {
			return "Rinkeby";
		}
		return "MainNet";
	}

	public static void main(String[] args) {
		// 1: MainNet, 4：Rinkeby
		String networkId = "1";
		String networkName = getNetworkName(networkId);
		String country = ""; // China
		ListStaticNodes staticNodes = new ListStaticNodes();
		try {
			List<ENode> eNodes = staticNodes.getAllStaticNodes(networkId, country);
			List<ENodePingResult> eNodePingResult = pingEthNode.getAllResults();
			String formatedENodeResultList = pingEthNode.formatEnodeResultList(eNodePingResult);
			String formatedENodeJSON = staticNodes.formatEnodeResultList(eNodePingResult);

			if(!new File(networkName).exists()) {
				new File(networkName).mkdirs();
			}
			try (BufferedOutputStream out = new BufferedOutputStream(
					new FileOutputStream(networkName + "/static-nodes.json"))) {
				out.write(formatedENodeJSON.getBytes("UTF-8"));
			}
			try (BufferedOutputStream out = new BufferedOutputStream(
					new FileOutputStream(networkName + "/static-nodes.txt"))) {
				out.write(formatedENodeResultList.getBytes("UTF-8"));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			PingEthNode.threadPool.shutdownNow();
		}
	}

	private String formatEnodeList(List<ENode> enodeList) throws IOException {
		JSONArray result = new JSONArray();
		for (ENode node : enodeList) {
			try {
				String enodeStr = node.getEnode();
				result.add(enodeStr);
			}catch(UnknownHostException e) {
				// do nothing
			}
		}
		return JSONObject.toJSONString(result, true);
	}

	private String formatEnodeResultList(List<ENodePingResult> enodeList) throws IOException {
		JSONArray result = new JSONArray();
		List<ENode> newList = new ArrayList<ENode>();
		List<ENode> timeoutList = new ArrayList<ENode>();
		for (ENodePingResult node : enodeList) {
			long pingTime = node.getPingTime();
			if (pingTime < 0) {
				timeoutList.add(node.getEnode());
			} else {
				newList.add(node.getEnode());
			}
		}
		newList.addAll(timeoutList);
		return formatEnodeList(newList);
	}

	public String getAndFormatENodes(String networkId, String country) throws IOException {
		List<ENode> eNodes = getAllStaticNodes(networkId, country);

		return formatEnodeList(eNodes);
	}

	public List<ENode> getAllStaticNodes(String networkId, String country) throws IOException {
		List<ENode> totalNodes = new ArrayList<ENode>();
		HttpClientBuilder httpClientBuilder = getHttpClientBuilder();

		try (CloseableHttpClient httpClient = httpClientBuilder.build()) {
			JSONObject firstPage = getSingleNodeStates(httpClient, networkId, country, 0, PAGE_ITEM_SIZE);
			// int recordTotal = firstPage.getInteger("recordsTotal");
			int recordsFiltered = firstPage.getInteger("recordsFiltered");
			addNodes(totalNodes, firstPage);
			if (recordsFiltered > PAGE_ITEM_SIZE) {
				int totalPage = recordsFiltered % PAGE_ITEM_SIZE == 0 ? recordsFiltered / PAGE_ITEM_SIZE
						: (recordsFiltered / PAGE_ITEM_SIZE + 1);
				System.out.println("totalRecords=" + recordsFiltered + ", pages=" + totalPage);
				for (int i = 1; i < totalPage; i++) {
					System.out.println("getting page " + i);
					JSONObject page = getSingleNodeStates(httpClient, networkId, country, i, PAGE_ITEM_SIZE);
					if (page != null) {
						addNodes(totalNodes, page);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw e;
		}
		return totalNodes;
	}

	private void addNodes(List<ENode> totalNodes, JSONObject page) {
		JSONArray data = page.getJSONArray("data");
		List<ENode> eNodeList = new ArrayList<ENode>();
		for (int i = 0; i < data.size(); i++) {
			JSONObject detailJSON = data.getJSONObject(i);
			ENode eNode = detailJSON.toJavaObject(ENode.class);
			eNodeList.add(eNode);
			totalNodes.add(eNode);
		}
		pingEthNode.pingNodes(eNodeList);
	}

	public JSONObject getSingleNodeStates(CloseableHttpClient httpClient, String networkId, String country, int start,
			int length) throws IOException {
		String url = getUrl(networkId, country, start, length);
		HttpGet httpGet = new HttpGet(url);
		httpGet.setConfig(getRequestConfig());
		try (CloseableHttpResponse result = httpClient.execute(httpGet)) {
			if (result.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				String str = EntityUtils.toString(result.getEntity(), "utf-8");
				return JSONObject.parseObject(str);
			} else {
				throw new IOException("Error On Read URL " + url);
			}
		} catch (SocketTimeoutException e) {
			e.printStackTrace();
			//throw e;
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			//throw e;
			return null;
		}
	}

	private HttpClientBuilder getHttpClientBuilder() {
		return HttpClients.custom()
				// 设置连接池管理
				.setConnectionManager(pool)
				// 设置请求配置
				.setDefaultRequestConfig(requestConfig)
				// 设置重试次数
				.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));
	}

	private RequestConfig getRequestConfig() {
		// Non-Proxy
		return RequestConfig.custom().setSocketTimeout(2000).setConnectTimeout(2000).build();
		//HttpHost proxy = new HttpHost("127.0.0.1", 1880, "http");
		//return RequestConfig.custom().setSocketTimeout(2000).setConnectTimeout(2000).setProxy(proxy).build();
	}

	private String getUrl(String networkId, String country, int start, int length) {
		return "https://www.ethernodes.org/network/" + networkId
				+ "/data?draw=1&columns[0][data]=id&columns[0][name]=&columns[0][searchable]=true&columns[0][orderable]=true&columns[0][search][value]=&columns[0][search][regex]=false&columns[1][data]=host&columns[1][name]=&columns[1][searchable]=true&columns[1][orderable]=true&columns[1][search][value]=&columns[1][search][regex]=false&columns[2][data]=port&columns[2][name]=&columns[2][searchable]=true&columns[2][orderable]=true&columns[2][search][value]=&columns[2][search][regex]=false&columns[3][data]=country&columns[3][name]=&columns[3][searchable]=true&columns[3][orderable]=true&columns[3][search][value]=&columns[3][search][regex]=false&columns[4][data]=clientId&columns[4][name]=&columns[4][searchable]=true&columns[4][orderable]=true&columns[4][search][value]=&columns[4][search][regex]=false&columns[5][data]=client&columns[5][name]=&columns[5][searchable]=true&columns[5][orderable]=true&columns[5][search][value]=&columns[5][search][regex]=false&columns[6][data]=clientVersion&columns[6][name]=&columns[6][searchable]=true&columns[6][orderable]=true&columns[6][search][value]=&columns[6][search][regex]=false&columns[7][data]=os&columns[7][name]=&columns[7][searchable]=true&columns[7][orderable]=true&columns[7][search][value]=&columns[7][search][regex]=false&columns[8][data]=lastUpdate&columns[8][name]=&columns[8][searchable]=true&columns[8][orderable]=true&columns[8][search][value]=&columns[8][search][regex]=false&order[0][column]=3&order[0][dir]=asc&start="
				+ start * PAGE_ITEM_SIZE + "&length=" + length + "&search[value]=" + country
				+ "&search[regex]=false&=1538707169908";
	}
}
