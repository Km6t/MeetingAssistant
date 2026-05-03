package com.xjtucsse.meetingassistant.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xjtucsse.meetingassistant.ApiClient;
import com.xjtucsse.meetingassistant.CloudException;
import com.xjtucsse.meetingassistant.DatabaseDAO;
import com.xjtucsse.meetingassistant.MeetingActivity;
import com.xjtucsse.meetingassistant.MeetingInfo;
import com.xjtucsse.meetingassistant.R;
import com.xjtucsse.meetingassistant.ReminderScheduler;

import java.util.ArrayList;
import java.util.List;

public class MeetingAdapter extends RecyclerView.Adapter<MeetingAdapter.LinearViewHolder> {
    private final Context context;
    private final List<MeetingInfo> meetings;

    public MeetingAdapter(Context hostContext, List<MeetingInfo> meetingList) {
        context = hostContext;
        meetings = meetingList == null ? new ArrayList<>() : meetingList;
    }

    @NonNull
    @Override
    public LinearViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.linear_item, parent, false);
        return new LinearViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LinearViewHolder holder, int position) {
        MeetingInfo meeting = meetings.get(position);
        holder.topicText.setText(meeting.meetingTopic);
        holder.timeText.setText("时间：" + meeting.getStartTimeText() + " - " + meeting.getEndTimeText());
        holder.metaText.setText(buildMetaLine(meeting));
        holder.statusText.setText(buildStatusLine(meeting));

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, MeetingActivity.class);
            intent.putExtra("meeting_id", meeting.meetingID);
            context.startActivity(intent);
        });

        holder.deleteButton.setOnClickListener(v -> new AlertDialog.Builder(v.getContext())
                .setTitle(meeting.isOrganizer() ? "删除会议" : "退出会议")
                .setMessage(meeting.isOrganizer()
                        ? "确定删除“" + meeting.meetingTopic + "”吗？删除后所有参会人都会失去这场会议。"
                        : "确定退出“" + meeting.meetingTopic + "”吗？")
                .setPositiveButton(meeting.isOrganizer() ? "删除" : "退出", (dialog, which) -> deleteMeeting(holder, meeting))
                .setNegativeButton("取消", null)
                .show());
    }

    @Override
    public int getItemCount() {
        return meetings.size();
    }

    public void replaceItems(List<MeetingInfo> meetingList) {
        meetings.clear();
        if (meetingList != null) {
            meetings.addAll(meetingList);
        }
        notifyDataSetChanged();
    }

    private void deleteMeeting(LinearViewHolder holder, MeetingInfo meeting) {
        new Thread(() -> {
            try {
                new ApiClient(context).deleteOrLeaveMeeting(meeting.meetingID);
                new DatabaseDAO(context).delete(meeting.meetingID);
                ReminderScheduler.cancelReminder(context, meeting.meetingID);
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition >= 0 && adapterPosition < meetings.size()) {
                    meetings.remove(adapterPosition);
                }
                holder.itemView.post(() -> {
                    notifyDataSetChanged();
                    Toast.makeText(
                            context,
                            meeting.isOrganizer() ? "会议已删除。" : "你已退出该会议。",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            } catch (CloudException e) {
                holder.itemView.post(() -> Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private String buildMetaLine(MeetingInfo meeting) {
        String organizer = meeting.organizer == null || meeting.organizer.trim().isEmpty()
                ? "组织者未填写"
                : "组织者：" + meeting.organizer;
        String location = meeting.locationName == null || meeting.locationName.trim().isEmpty()
                ? "地点未填写"
                : "地点：" + meeting.locationName;
        return organizer + "  |  " + location;
    }

    private String buildStatusLine(MeetingInfo meeting) {
        String reminder = meeting.reminderMinutes > 0 ? "提醒：" + meeting.reminderMinutes + " 分钟前" : "提醒：关闭";
        String attendeeSummary = "参会：" + meeting.getExpectedCount() + " 人";
        String locationGate = meeting.hasLocationGate() ? "签到半径：" + meeting.checkInRadiusMeters + " 米" : "无需定位签到";
        String role = meeting.isOrganizer() ? "身份：组织者" : "身份：参会人";
        return reminder + "  |  " + attendeeSummary + "  |  " + locationGate + "  |  " + role;
    }

    static class LinearViewHolder extends RecyclerView.ViewHolder {
        private final TextView topicText;
        private final TextView timeText;
        private final TextView metaText;
        private final TextView statusText;
        private final ImageButton deleteButton;

        LinearViewHolder(@NonNull View itemView) {
            super(itemView);
            topicText = itemView.findViewById(R.id.tv1);
            timeText = itemView.findViewById(R.id.tv2);
            metaText = itemView.findViewById(R.id.tv3);
            statusText = itemView.findViewById(R.id.tv4);
            deleteButton = itemView.findViewById(R.id.btn_del_note);
        }
    }
}
