package com.xjtucsse.meetingassistant.ui.MeetingsNow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xjtucsse.meetingassistant.DatabaseDAO;
import com.xjtucsse.meetingassistant.MeetingInfo;
import com.xjtucsse.meetingassistant.R;
import com.xjtucsse.meetingassistant.ui.MeetingAdapter;
import com.xjtucsse.meetingassistant.ui.MeetingsAhead.MeetingsAheadFragment;

import java.util.ArrayList;
import java.util.List;

public class MeetingsNowFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyStateText;
    private MeetingAdapter meetingAdapter;
    private final List<MeetingInfo> meetings = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewModelProviders.of(this).get(MeetingsNowViewModel.class);
        View view = inflater.inflate(R.layout.fragment_meetings_ahead, container, false);
        recyclerView = view.findViewById(R.id.rv1);
        emptyStateText = view.findViewById(R.id.empty_state);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        recyclerView.addItemDecoration(new MeetingsAheadFragment.SpacesItemDecoration(8));
        meetingAdapter = new MeetingAdapter(getActivity(), meetings);
        recyclerView.setAdapter(meetingAdapter);
        reloadFromLocalCache();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadFromLocalCache();
    }

    public void reloadFromLocalCache() {
        if (getContext() == null || meetingAdapter == null) {
            return;
        }
        List<MeetingInfo> latestMeetings = new DatabaseDAO(getContext()).listOngoingMeetings();
        meetingAdapter.replaceItems(latestMeetings);
        emptyStateText.setVisibility(latestMeetings.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
