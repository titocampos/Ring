package com.mecatronica.ring;

public interface BluetoothListener {
    public void sendMessage(String input);
    public boolean bluetoothIsOn();
    public void connectDevice();
    public void disconnectDevice();
    public String getDeviceName();
}
