package com.github.smartheye.eth.tools;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PingEthNode {

	static final int PING_TIME_OUT = 3000;

	static ExecutorService threadPool = Executors.newFixedThreadPool(100);

	private List<ENodePingResult> enodeResultList = new ArrayList<ENodePingResult>();

	
	public void pingNodes(List<ENode> eNodeList) {
		for (int i = 0; i < eNodeList.size(); i++) {
			final ENodePingResult result = new ENodePingResult();
			final ENode enode = eNodeList.get(i);

			result.setEnode(enode);
			final String host = enode.getHost();
			Future<Long> future = threadPool.submit(() -> {
				final long start = System.currentTimeMillis();
				result.setStartTimeMillis(start);
				try {
					InetAddress address = InetAddress.getByName(host);
					boolean reachable = address.isReachable(PING_TIME_OUT);
					long time = System.currentTimeMillis() - start;
					//System.out
					//		.println("Id " + result.getEnode().getId() + ", ping " + host + ", timeout=" + time + "ms");
					if (reachable) {
						return Long.valueOf(time);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				return -1L;
			});

			result.setFuture(future);
			enodeResultList.add(result);
		}
	}

	public List<ENodePingResult> getAllResults() {
		List<ENodePingResult> newENodeResultList = new ArrayList<ENodePingResult>();
		newENodeResultList.addAll(enodeResultList);

		while (!newENodeResultList.isEmpty()) {
			Iterator<ENodePingResult> iter = newENodeResultList.iterator();
			List<ENodePingResult> removedList = new ArrayList<ENodePingResult>();
			while (iter.hasNext()) {
				ENodePingResult result = iter.next();
				if (result.getStartTimeMillis() > 0) {
					long current = System.currentTimeMillis();
					long timePassed = current - result.getStartTimeMillis();
					if (timePassed > PING_TIME_OUT) {
						if (result.getFuture().isDone()) {
							try {
								result.setPingTime(result.getFuture().get());
							} catch (InterruptedException | ExecutionException e) {
								result.setPingTime(-1L);
							}
						} else {
							result.getFuture().cancel(true);
							result.setPingTime(-1L);
						}
						// iter.remove();
						removedList.add(result);
					}
				}
				//System.out.println(result);
			}
			for (ENodePingResult result : removedList) {
				newENodeResultList.remove(result);
			}
			if (!newENodeResultList.isEmpty()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		return sortENodeList();
	}

	public List<ENodePingResult> sortENodeList() {
		enodeResultList.sort(new Comparator<ENodePingResult>() {

			@Override
			public int compare(ENodePingResult o1, ENodePingResult o2) {
				long diff = o1.getPingTime() - o2.getPingTime();
				if (diff > 0) {
					return 1;
				} else if (diff < 0) {
					return -1;
				} else {
					return 0;
				}
			}
		});
		return enodeResultList;
	}

	public List<ENodePingResult> getEnodeResultList() {
		return enodeResultList;
	}

	public String formatEnodeResultList(List<ENodePingResult> enodeResultList) throws IOException {
		List<ENodePingResult> timeoutNodes = new ArrayList<ENodePingResult>();
		List<ENodePingResult> newNodes = new ArrayList<ENodePingResult>();
		for(ENodePingResult enode:enodeResultList) {
			if(enode.getPingTime()<0) {
				timeoutNodes.add(enode);
			}else {
				newNodes.add(enode);
			}
		}
		newNodes.addAll(timeoutNodes);
		return internalFormatEnodeResultList(newNodes);
	}
	
	public String internalFormatEnodeResultList(List<ENodePingResult> enodeResultList) throws IOException {
		StringBuffer sbf = new StringBuffer();
		for (ENodePingResult result : enodeResultList) {
			long pingTime = result.getPingTime();
			String pingStr = "timeout";
			if (pingTime > 0) {
				pingStr = pingTime + "ms";
			}
			sbf.append(result.getEnode().getEnode()).append("  // ").append(pingStr).append(System.getProperty("line.separator"));
		}
		return sbf.toString();
	}
}
