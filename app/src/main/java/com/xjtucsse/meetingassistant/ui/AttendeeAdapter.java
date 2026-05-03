package com.xjtucsse.meetingassistant.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xjtucsse.meetingassistant.AttendeeInfo;
import com.xjtucsse.meetingassistant.R;

import java.util.ArrayList;
import java.util.List;

public class AttendeeAdapter extends RecyclerView.Adapter<AttendeeAdapter.AttendeeViewHolder> {
    public interface OnAttendeeClickListener {
        void onAttendeeClick(int position);
    }

    private final List<AttendeeInfo> attendees;
    private final OnAttendeeClickListener clickListener;

    public AttendeeAdapter(List<AttendeeInfo> attendeeList, OnAttendeeClickListener listener) {
        attendees = attendeeList == null ? new ArrayList<>() : attendeeList;
        clickListener = listener;
    }

    @NonNull
    @Override
    public AttendeeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendee, parent, false);
        return new AttendeeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AttendeeViewHolder holder, int position) {
        AttendeeInfo attendee = attendees.get(position);
        holder.nameText.setText(attendee.getDisplayName());
        holder.statusText.setText(AttendeeInfo.getDisplayStatus(attendee.status));
        holder.timeText.setText(
                attendee.checkInTime == null || attendee.checkInTime.trim().isEmpty()
                        ? "尚未签到"
                        : "签到时间：" + attendee.checkInTime
        );
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onAttendeeClick(holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return attendees.size();
    }

    public void updateItems(List<AttendeeInfo> attendeeList) {
        attendees.clear();
        if (attendeeList != null) {
            attendees.addAll(attendeeList);
        }
        notifyDataSetChanged();
    }

    static class AttendeeViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final TextView statusText;
        private final TextView timeText;

        AttendeeViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.attendee_name);
            statusText = itemView.findViewById(R.id.attendee_status);
            timeText = itemView.findViewById(R.id.attendee_time);
        }
    }
}
