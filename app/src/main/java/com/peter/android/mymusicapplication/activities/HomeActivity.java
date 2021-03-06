package com.peter.android.mymusicapplication.activities;

import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.google.android.material.button.MaterialButton;
import com.peter.android.mymusicapplication.LoadSomePostsQuery;
import com.peter.android.mymusicapplication.R;
import com.peter.android.mymusicapplication.adapters.AudioBlogsRvAdapter;
import com.peter.android.mymusicapplication.apollo.ApolloFactory;
import com.peter.android.mymusicapplication.models.AudioBlogModel;
import com.peter.android.mymusicapplication.models.AudioPlayerActivityModel;
import com.peter.android.mymusicapplication.services.OnClearFromRecentService;
import com.peter.android.mymusicapplication.services.PlayerService;

import org.imaginativeworld.oopsnointernet.ConnectionCallback;
import org.imaginativeworld.oopsnointernet.NoInternetDialog;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;


public class HomeActivity extends AppCompatActivity implements AudioBlogsRvAdapter.OnItemClicked {


    private SeekBar sbProgress;
    private TextView tvTime;
    private TextView tvDuration;
    private TextView songText;


    private GuiReceiver receiver;
    private Handler handler = new Handler();
    private boolean blockGUIUpdate;
    private RecyclerView audioBlogRv;
    private volatile AudioPlayerActivityModel activityModel = new AudioPlayerActivityModel();
    private AudioBlogsRvAdapter audioBlogAdapter;
    // No Internet Dialog
    private NoInternetDialog noInternetDialog;
    private ProgressDialog progressDialog;
    private MaterialButton playBtn;

    private static String getTimeString(int totalTime) {
        long s = totalTime % 60;
        long m = (totalTime / 60) % 60;
        long h = totalTime / 3600;

        String stringTotalTime;
        if (h != 0)
            stringTotalTime = String.format(Locale.ENGLISH, "%02d:%02d:%02d", h, m, s);
        else
            stringTotalTime = String.format(Locale.ENGLISH, "%02d:%02d", m, s);
        return stringTotalTime;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey("activityModel")) {
            activityModel = savedInstanceState.getParcelable("activityModel");
        }
        setContentView(R.layout.activity_home);
        OnClearFromRecentService.startActionOpen(getApplicationContext());
        createProgressDialog();// loading dialog
        // No Internet Dialog
        NoInternetDialog.Builder builder1 = new NoInternetDialog.Builder(this);

        builder1.setConnectionCallback(new ConnectionCallback() { // Optional
            @Override
            public void hasActiveConnection(boolean hasActiveConnection) {
                if (hasActiveConnection) {
                    if (activityModel.getListOfBlogsUI().isEmpty()) {
                        getDataToService();
                    } else {
                        Toast.makeText(getApplicationContext(), "Welcome Back :)", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    if (activityModel.getListOfBlogsUI().isEmpty()) {
                        Intent myService = new Intent(HomeActivity.this, PlayerService.class);
                        stopService(myService);
                    } else {
                        if (isMyServiceRunning(PlayerService.class)) {
                            PlayerService.startActionPause(HomeActivity.this);
                            PlayerService.startCancelNotification(HomeActivity.this);// prevent user from play outside the internet
                        }
                    }
                }
            }
        });
        builder1.setCancelable(false); // Optional
        builder1.setNoInternetConnectionTitle("No Internet"); // Optional
        builder1.setNoInternetConnectionMessage("Check your Internet connection and try again"); // Optional
        builder1.setShowInternetOnButtons(true); // Optional
        builder1.setPleaseTurnOnText("Please turn on"); // Optional
        builder1.setWifiOnButtonText("Wifi"); // Optional
        builder1.setMobileDataOnButtonText("Mobile data"); // Optional

        builder1.setOnAirplaneModeTitle("No Internet"); // Optional
        builder1.setOnAirplaneModeMessage("You have turned on the airplane mode."); // Optional
        builder1.setPleaseTurnOffText("Please turn off"); // Optional
        builder1.setAirplaneModeOffButtonText("Airplane mode"); // Optional
        builder1.setShowAirplaneModeOffButtons(true); // Optional

        noInternetDialog = builder1.build();
        audioBlogRv = findViewById(R.id.rv_audioBlog);
        setUpRv();
//        getDataToSerivce();
        initilizeViews();
    }

    private void createProgressDialog() {
        progressDialog = new ProgressDialog(HomeActivity.this, R.style.AppCompatAlertDialogStyle);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setMessage(getString(R.string.loading));
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(true);
    }

    private void getDataToService() {
        HomeActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (progressDialog != null && !progressDialog.isShowing())
                    progressDialog.show();
            }
        });

        ApolloFactory.getApolloClient().query(LoadSomePostsQuery.builder().build()).enqueue(new ApolloCall.Callback<LoadSomePostsQuery.Data>() {
            @Override
            public void onResponse(@NotNull Response<LoadSomePostsQuery.Data> response) {
                List<LoadSomePostsQuery.Post> audioPosts = Objects.requireNonNull(response.getData()).posts();
                handler.post(() -> {
                    for (LoadSomePostsQuery.Post audioPost : audioPosts) {
                        activityModel.addToListOfBlogsUI(new AudioBlogModel(audioPost));
                        activityModel.setCurrentSelected(0);
                    }
                    audioBlogAdapter.notifyDataSetChanged();

                    if (!isMyServiceRunning(PlayerService.class)) {
                        PlayerService.startActionSetPlaylist(HomeActivity.this, activityModel);
                        PlayerService.startActionSelectAudio(HomeActivity.this, 0);
                        PlayerService.startActionPause(HomeActivity.this);// saying our state after loading
                    }

                    if (receiver == null) {
                        receiver = new GuiReceiver();
                        receiver.setPlayerActivity(HomeActivity.this);
                    }

                    IntentFilter filter = new IntentFilter();
                    filter.addAction(PlayerService.GUI_UPDATE_ACTION);
                    filter.addAction(PlayerService.NEXT_ACTION);
                    filter.addAction(PlayerService.SELECT_ACTION);
                    filter.addAction(PlayerService.PREVIOUS_ACTION);
                    filter.addAction(PlayerService.PLAY_ACTION);
                    filter.addAction(PlayerService.PAUSE_ACTION);
                    filter.addAction(PlayerService.LOADED_ACTION);
                    filter.addAction(PlayerService.LOADING_ACTION);
                    filter.addAction(PlayerService.DELETE_ACTION);
                    filter.addAction(PlayerService.COMPLETE_ACTION);
                    registerReceiver(receiver, filter);
                    PlayerService.startActionSendInfoBroadcast(HomeActivity.this);
                    HomeActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (progressDialog != null && progressDialog.isShowing())
                                progressDialog.dismiss();
                        }
                    });
                });
            }

            @Override
            public void onFailure(@NotNull ApolloException e) {
                HomeActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (progressDialog != null && progressDialog.isShowing())
                            progressDialog.dismiss();
                        Toast.makeText(HomeActivity.this, "Connection Error Check th API or Internet Connection", Toast.LENGTH_LONG).show();
                    }
                });
                Log.e("Apolo Error", e.toString());// should we retry ?

            }
        });
    }

    private void setUpRv() {
        audioBlogAdapter = new AudioBlogsRvAdapter(activityModel.getListOfBlogsUI(), activityModel.currentSelected, this, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        audioBlogRv.setLayoutManager(layoutManager);
        audioBlogRv.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));
        audioBlogRv.setItemAnimator(new DefaultItemAnimator());
        audioBlogRv.setAdapter(audioBlogAdapter);
    }

    private void initilizeViews() {

        songText = findViewById(R.id.tv_audio_name);

        tvTime = (TextView) findViewById(R.id.tvTime);
        String stringActualTime = String.format("%02d:%02d", 0, 0);
        tvTime.setText(stringActualTime);


//        long s = song.getDuration() % 60;
//        long m = (song.getDuration() / 60) % 60;
//        long h = song.getDuration() / 3600;

//        String stringTotalTime;
//        if (h != 0)
//            stringTotalTime = String.format("%02d:%02d:%02d", h, m, s);
//        else
//            stringTotalTime = String.format("%02d:%02d", m, s);
        tvDuration = (TextView) findViewById(R.id.tvDuration);
//        tvDuration.setText(stringTotalTime);

        sbProgress = (SeekBar) findViewById(R.id.seekBar);
        sbProgress.setClickable(false);

//        sbProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//            int time;
//
//            @Override
//            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                time = progress;
//
//                sbProgress.setProgress(this.time);
//                if (fromUser)
//                    tvTime.setText(getTimeString(time));
//            }
//
//            @Override
//            public void onStartTrackingTouch(SeekBar seekBar) {
//                blockGUIUpdate = true;
//            }
//
//            @Override
//            public void onStopTrackingTouch(SeekBar seekBar) {
//                unblockGUIUpdate();
//                setTime(time);
//            }
//        });
        playBtn = findViewById(R.id.button);
        if (activityModel.isPlaying()) {
            if (playBtn.getTag() == null || !playBtn.getTag().equals("Play")) {
                playBtn.setIcon(ContextCompat.getDrawable(HomeActivity.this, R.drawable.ic_pause));
                playBtn.setTag("Play");
            }
        } else {
            if (playBtn.getTag() == null || !playBtn.getTag().equals("Play")) {
                playBtn.setIcon(ContextCompat.getDrawable(HomeActivity.this, R.drawable.ic_play_arrow));
                playBtn.setTag("Pause");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();


        if (activityModel.currentSelected != -1) {
            if (receiver == null) {
                receiver = new GuiReceiver();
                receiver.setPlayerActivity(this);
            }

            IntentFilter filter = new IntentFilter();
            filter.addAction(PlayerService.GUI_UPDATE_ACTION);
            filter.addAction(PlayerService.NEXT_ACTION);
            filter.addAction(PlayerService.SELECT_ACTION);
            filter.addAction(PlayerService.PREVIOUS_ACTION);
            filter.addAction(PlayerService.PLAY_ACTION);
            filter.addAction(PlayerService.PAUSE_ACTION);
            filter.addAction(PlayerService.LOADED_ACTION);
            filter.addAction(PlayerService.LOADING_ACTION);
            filter.addAction(PlayerService.DELETE_ACTION);
            filter.addAction(PlayerService.COMPLETE_ACTION);
            registerReceiver(receiver, filter);
            PlayerService.startActionSendInfoBroadcast(this);
        }
    }

    private void setTime(int time) {
        PlayerService.startActionSeekTo(this, time * 1000);
    }

    private void unblockGUIUpdate() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                blockGUIUpdate = false;
            }
        }, 150);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (receiver != null)
            unregisterReceiver(receiver);
    }

    public void onPlayOrPause(View view) {
        if (activityModel.isPlaying()) {
            PlayerService.startActionPause(this);
        } else {
            PlayerService.startActionPlay(this);
        }
    }

    public void play(View view) {
        PlayerService.startActionPlay(this);
    }

    public void pause(View view) {
        PlayerService.startActionPause(this);
    }

    public void next(View view) {
        PlayerService.startActionNextSong(this);
        PlayerService.startActionSendInfoBroadcast(this);
    }

    public void previous(View view) {
        PlayerService.startActionPreviousSong(this);
        PlayerService.startActionSendInfoBroadcast(this);
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onItemClick(View view, int position) {
        switch (view.getId()) {
            case R.id.item_view:
            case R.id.tv_date:
            case R.id.tv_size:
            case R.id.tv_name:
                PlayerService.startActionPause(this);
                activityModel.setCurrentSelected(position);
                audioBlogAdapter.setSelected(position);
                PlayerService.startActionSelectAudio(this, position);
                PlayerService.startActionSendInfoBroadcast(this);
                PlayerService.startActionPlay(this);
                break;

            case R.id.keepPlaying_cb:

                PlayerService.startActionKeepPlaying(this, position, ((CheckBox) view).isChecked());


                break;
        }

    }

    @Override
    public void onBackPressed() {
        try {
            super.onBackPressed();
            PlayerService.startCancelNotification(HomeActivity.this);

            PlayerService.startActionKillService(getApplicationContext());

            OnClearFromRecentService.startActionClose(getApplicationContext());
            this.finish();



        } catch (Exception e) {
            // we killed the process no need to be concerned
        }finally {
            activityModel = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        noInternetDialog.onDestroy();
        noInternetDialog.destroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("activityModel", activityModel);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.containsKey("activityModel")) {
            activityModel = savedInstanceState.getParcelable("activityModel");
        }
    }

    private static class GuiReceiver extends BroadcastReceiver {

        private HomeActivity playerActivity;
        private int actualTime;

        public void setPlayerActivity(HomeActivity playerActivity) {
            this.playerActivity = playerActivity;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(PlayerService.GUI_UPDATE_ACTION)) {
                if (intent.hasExtra(PlayerService.TOTAL_TIME_VALUE_EXTRA)) {
                    int totalTime = intent.getIntExtra(PlayerService.TOTAL_TIME_VALUE_EXTRA, 0) / 1000;
                    Log.e("Total Time",totalTime+"");
                    if (playerActivity.sbProgress != null)
                        playerActivity.sbProgress.setMax(totalTime);
                    String stringTotalTime = getTimeString(totalTime);
                    if (playerActivity.tvDuration != null)
                        playerActivity.tvDuration.setText(stringTotalTime);
                }

                if (intent.hasExtra(PlayerService.ACTUAL_TIME_VALUE_EXTRA)) {
                    if (playerActivity.blockGUIUpdate)
                        return;

                    actualTime = intent.getIntExtra(PlayerService.ACTUAL_TIME_VALUE_EXTRA, 0) / 1000;

                    String time = getTimeString(actualTime);

                    if (playerActivity.sbProgress != null) {
                        playerActivity.sbProgress.setProgress(actualTime);
                    }
                    if (playerActivity.tvTime != null)
                        playerActivity.tvTime.setText(time);
                    // when we select random song player isn't ready to say the whole time yet(buffering) but it can say time spend so far
                    // as a work around we will ask player each time he send a progress about song total time
                    // some files also may have brocken ending and we need to know how we gonna recognise that
                    if(playerActivity.tvDuration.getText().equals(getTimeString(0))&&actualTime != 0)
                    PlayerService.startActionSendInfoBroadcast(playerActivity);// should we try for an exact time
                }

                if (intent.hasExtra(PlayerService.COVER_URL_EXTRA)) {
                    String cover = intent.getStringExtra(PlayerService.COVER_URL_EXTRA);
//                    Picasso.with(playerActivity).load(cover).fit().centerCrop().into(playerActivity.ivCover);
                }

                if (intent.hasExtra(PlayerService.SONG_NUM_EXTRA)) {
                    int num = intent.getIntExtra(PlayerService.SONG_NUM_EXTRA, 0);
                    playerActivity.activityModel.setCurrentSelected(num);
                    playerActivity.audioBlogAdapter.setSelected(num);
                    playerActivity.audioBlogAdapter.notifyDataSetChanged();
                    playerActivity.songText.setText(playerActivity.activityModel.getListOfBlogsUI().get(num).getTitle());

                }

                if (intent.hasExtra(PlayerService.PLAYER_IS_PLAYING)) {
                    playerActivity.activityModel.setPlaying(intent.getBooleanExtra(PlayerService.PLAYER_IS_PLAYING, false));
                }
            }
            if (intent.getAction().equals(PlayerService.SELECT_ACTION)) {
                PlayerService.startActionSendInfoBroadcast(playerActivity);
            }
            if (intent.getAction().equals(PlayerService.PAUSE_ACTION)) {
                playerActivity.activityModel.setPlaying(false);

            }
            if (intent.getAction().equals(PlayerService.PLAY_ACTION)) {
                playerActivity.activityModel.setPlaying(true);
            }

            if (intent.getAction().equals(PlayerService.COMPLETE_ACTION)) {
                playerActivity.activityModel.setPlaying(false);
            }

            if (intent.getAction().equals(PlayerService.NEXT_ACTION)) {
                if (intent.hasExtra(PlayerService.PLAYER_IS_PLAYING)) {
                    playerActivity.activityModel.setPlaying(intent.getBooleanExtra(PlayerService.PLAYER_IS_PLAYING, false));
                }
            }

            if (intent.getAction().equals(PlayerService.PREVIOUS_ACTION)) {
                if (intent.hasExtra(PlayerService.PLAYER_IS_PLAYING)) {
                    playerActivity.activityModel.setPlaying(intent.getBooleanExtra(PlayerService.PLAYER_IS_PLAYING, false));
                }
            }
            if (playerActivity.activityModel.isPlaying()) {
                if (playerActivity.playBtn.getTag() == null || !playerActivity.playBtn.getTag().equals("Play")) {
                    playerActivity.playBtn.setIcon(ContextCompat.getDrawable(playerActivity, R.drawable.ic_pause));
                    playerActivity.playBtn.setTag("Play");
                }
            } else {
                if (playerActivity.playBtn.getTag() == null || !playerActivity.playBtn.getTag().equals("Pause")) {
                    playerActivity.playBtn.setIcon(ContextCompat.getDrawable(playerActivity, R.drawable.ic_play_arrow));
                    playerActivity.playBtn.setTag("Pause");
                }
            }
            // we should handle error if he is a bad boy :D
            if (intent.getAction().equals(PlayerService.DELETE_ACTION))
                if (playerActivity != null)
                    playerActivity.finish();
                else
                    PlayerService.startActionSendInfoBroadcast(playerActivity);
        }
    }
}