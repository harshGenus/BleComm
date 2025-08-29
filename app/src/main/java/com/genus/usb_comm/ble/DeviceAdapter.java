package com.genus.usb_comm.ble;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.genus.usb_comm.R;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

    public interface OnDeviceClickListener {
        void onDeviceClick(DeviceItem device);
    }

    private List<DeviceItem> devices = new ArrayList<>();
    private OnDeviceClickListener listener;

    public DeviceAdapter(OnDeviceClickListener listener) {
        this.listener = listener;
    }

    public void setDevices(List<DeviceItem> newDevices) {
        this.devices = newDevices;
        notifyDataSetChanged();
    }

    public void addDevice(DeviceItem device) {
        if (!devices.contains(device)) {
            devices.add(device);
            notifyItemInserted(devices.size() - 1);
        }
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        DeviceItem device = devices.get(position);
        holder.tvName.setText(device.getName());
        holder.tvAddress.setText(device.getAddress());
        holder.itemView.setOnClickListener(v -> listener.onDeviceClick(device));
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvAddress;

        DeviceViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvDeviceName);
            tvAddress = itemView.findViewById(R.id.tvDeviceAddress);
        }
    }
}
