package com.phoenix.nattester.random;

import java.net.InetAddress;

import com.phoenix.nattester.TaskAppConfig;

public class ProbeTaskParam {
	private TaskAppConfig cfg;
	private int extPort;
	private int intPort;
	private InetAddress stunAddressCached;
	private int socketTimeout=300;
	public TaskAppConfig getCfg() {
		return cfg;
	}
	public void setCfg(TaskAppConfig cfg) {
		this.cfg = cfg;
	}
	public int getExtPort() {
		return extPort;
	}
	public void setExtPort(int extPort) {
		this.extPort = extPort;
	}
	public int getIntPort() {
		return intPort;
	}
	public void setIntPort(int intPort) {
		this.intPort = intPort;
	}
	public InetAddress getStunAddressCached() {
		return stunAddressCached;
	}
	public void setStunAddressCached(InetAddress stunAddressCached) {
		this.stunAddressCached = stunAddressCached;
	}
	public int getSocketTimeout() {
		return socketTimeout;
	}
	public void setSocketTimeout(int socketTimeout) {
		this.socketTimeout = socketTimeout;
	}
	
}