package com.example.ipscanner;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

public class AdapterNetworkDevice
        extends RecyclerView.Adapter<AdapterNetworkDevice.ViewHolder> {

    private List<NetworkDevice> mNetworkDeviceList;

    public AdapterNetworkDevice(List<NetworkDevice> networkDeviceList) {
        mNetworkDeviceList = networkDeviceList;
    }

    @Override
    public AdapterNetworkDevice.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_network_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.bindNetworkDevice(mNetworkDeviceList.get(position));
    }

    @Override
    public int getItemCount() {
        return mNetworkDeviceList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView mTextViewIp;
        private TextView mTextViewMac;

        ViewHolder(View view) {
            super(view);
            mTextViewIp = view.findViewById(R.id.textViewIp);
            mTextViewMac = view.findViewById(R.id.textViewMac);
        }

        void bindNetworkDevice(NetworkDevice networkDevice) {
            mTextViewIp.setText(networkDevice.getIpString());
            mTextViewMac.setText(networkDevice.getMacString());
        }
    }
}