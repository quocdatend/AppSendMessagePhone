package com.hongoquocdat.appsendmessagephone;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public static class HistoryItem {
        final String sender;
        final long timestamp;
        final String message;

        public HistoryItem(String sender, long timestamp, String message) {
            this.sender = sender;
            this.timestamp = timestamp;
            this.message = message;
        }
    }

    private final List<HistoryItem> items = new ArrayList<>();

    public void updateItems(List<HistoryItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryItem item = items.get(position);
        holder.message.setText(item.message);
        holder.meta.setText(item.sender + "  ·  " + formatTime(item.timestamp));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatTime(long timestamp) {
        Calendar msgCal = Calendar.getInstance();
        msgCal.setTimeInMillis(timestamp);
        Calendar now = Calendar.getInstance();

        SimpleDateFormat hourMin = new SimpleDateFormat("HH:mm", Locale.getDefault());

        boolean sameYear  = now.get(Calendar.YEAR)  == msgCal.get(Calendar.YEAR);
        boolean sameMonth = now.get(Calendar.MONTH) == msgCal.get(Calendar.MONTH);
        boolean sameDay   = now.get(Calendar.DATE)  == msgCal.get(Calendar.DATE);

        if (sameYear && sameMonth && sameDay) {
            return hourMin.format(new Date(timestamp));
        }

        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DATE, -1);
        boolean isYesterday = yesterday.get(Calendar.YEAR)  == msgCal.get(Calendar.YEAR)
                           && yesterday.get(Calendar.MONTH) == msgCal.get(Calendar.MONTH)
                           && yesterday.get(Calendar.DATE)  == msgCal.get(Calendar.DATE);

        if (isYesterday) {
            return "Hôm qua " + hourMin.format(new Date(timestamp));
        }

        return new SimpleDateFormat("dd/MM  HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView message;
        final TextView meta;

        ViewHolder(View view) {
            super(view);
            message = view.findViewById(R.id.historyMessage);
            meta    = view.findViewById(R.id.historyMeta);
        }
    }
}
