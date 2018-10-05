package com.github.smartheye.eth.tools;

import java.util.concurrent.Future;

public class ENodePingResult {

	private ENode enode;
	private long startTimeMillis = -1;
	private Future<Long> future;
	private long pingTime = -1;
	
	public ENode getEnode() {
		return enode;
	}

	public void setEnode(ENode enode) {
		this.enode = enode;
	}

	public long getStartTimeMillis() {
		return startTimeMillis;
	}

	public void setStartTimeMillis(long startTimeMillis) {
		this.startTimeMillis = startTimeMillis;
	}


	public Future<Long> getFuture() {
		return future;
	}

	public void setFuture(Future<Long> future) {
		this.future = future;
	}

	public long getPingTime() {
		return pingTime;
	}

	public void setPingTime(long pingTime) {
		this.pingTime = pingTime;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((enode == null) ? 0 : enode.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ENodePingResult other = (ENodePingResult) obj;
		if (enode == null) {
			if (other.enode != null)
				return false;
		} else if (!enode.equals(other.enode))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ENodePingResult [enode=" + enode + ", startTimeMillis=" + startTimeMillis + ", future=" + future
				+ ", pingTime=" + pingTime + "]";
	}



}
