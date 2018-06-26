package com.example.ipscanner;

public class NetworkDevice {
    private String ipString;
    private String macString;

    public NetworkDevice(String ip, String mac) {
        this.ipString = ip;
        this.macString = mac;
    }

    public String getIpString() {
        return ipString;
    }

    public void setIpString(String ipString) {
        this.ipString = ipString;
    }

    public String getMacString() {
        return macString;
    }

    public void setMacString(String macString) {
        this.macString = macString;
    }
}
