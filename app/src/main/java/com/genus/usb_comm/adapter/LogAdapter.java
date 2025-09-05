package com.genus.usb_comm.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.genus.usb_comm.R;
import com.genus.usb_comm.model.LogItem;

import java.util.ArrayList;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    private final List<LogItem> logs = new ArrayList<>();

    public void addLog(LogItem log) {
        logs.add(log);
        notifyItemInserted(logs.size() - 1);
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        LogItem log = logs.get(position);
        holder.textView.setText(log.getMessage());

        switch (log.getSource()) {
            case USB:
                holder.textView.setTextColor(Color.RED);
                break;
            case BLE:
                holder.textView.setTextColor(Color.BLUE);
                break;
            default:
                holder.textView.setTextColor(Color.BLACK);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.tvLogMessage);
        }
    }
}
