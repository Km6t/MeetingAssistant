package com.xjtucsse.meetingassistant.ui.MeetingsAhead;

import android.graphics.Rect;
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

import java.util.ArrayList;
import java.util.List;

public class MeetingsAheadFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyStateText;
    private MeetingAdapter meetingAdapter;
    private final List<MeetingInfo> meetings = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewModelProviders.of(this).get(MeetingsAheadViewModel.class);
        View view = inflater.inflate(R.layout.fragment_meetings_ahead, container, false);
        recyclerView = view.findViewById(R.id.rv1);
        emptyStateText = view.findViewById(R.id.empty_state);

        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        recyclerView.addItemDecoration(new SpacesItemDecoration(8));
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
        List<MeetingInfo> latestMeetings = new DatabaseDAO(getContext()).listUpcomingMeetings();
        meetingAdapter.replaceItems(latestMeetings);
        emptyStateText.setVisibility(latestMeetings.isEmpty() ? View.VISIBLE : View.GONE);
    }

    public static class SpacesItemDecoration extends RecyclerView.ItemDecoration {
        private final int space;

        public SpacesItemDecoration(int itemSpace) {
            space = itemSpace;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            outRect.left = space;
            outRect.right = space;
            outRect.bottom = space;
            if (parent.getChildAdapterPosition(view) == 0) {
                outRect.top = space;
            }
        }
    }
}
