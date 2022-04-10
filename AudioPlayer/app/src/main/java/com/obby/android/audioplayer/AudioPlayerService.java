package com.obby.android.audioplayer;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.cardview.widget.CardView;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.slider.Slider;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AudioPlayerService extends Service {
    private static final String INTENT_ACTION_STATE = "state";

    private static final String INTENT_ACTION_LOOP = "loop";

    private static final String INTENT_ACTION_EXIT = "exit";

    private static final String NOTIFICATION_CHANNEL_ID = "audio_player";

    private static final int NOTIFICATION_ID = 1;

    private MediaPlayer mMediaPlayer;

    private FloatingWindow mFloatingWindow;

    private NotificationCompat.Builder mNotificationBuilder;

    private SharedPreferences mSharedPreferences;

    private final Object mLock = new Object();

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private final ScheduledExecutorService mScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor();

    private volatile ScheduledFuture<?> mPlaybackPositionUpdateTask;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || mMediaPlayer == null) {
                return;
            }

            final String action = intent.getAction();
            if (INTENT_ACTION_STATE.equals(action)) {
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.pause();
                } else {
                    mMediaPlayer.start();
                }
                updatePlayback();
            } else if (INTENT_ACTION_LOOP.equals(action)) {
                mMediaPlayer.setLooping(!mMediaPlayer.isLooping());
                updatePlayback();
            } else if (INTENT_ACTION_EXIT.equals(action)) {
                exit();
            }
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener mOnPreferenceChangeListener =
            (sharedPreferences, key) -> {
                if ("floating_window".equals(key) && mFloatingWindow != null) {
                    if (sharedPreferences.getBoolean(key, false)
                            && Settings.canDrawOverlays(this)) {
                        mFloatingWindow.attach();
                    } else {
                        mFloatingWindow.detach();
                    }
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();

        // Register broadcast receiver
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(INTENT_ACTION_STATE);
        intentFilter.addAction(INTENT_ACTION_LOOP);
        intentFilter.addAction(INTENT_ACTION_EXIT);
        registerReceiver(mBroadcastReceiver, intentFilter);

        // Create media player
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnPreparedListener(mp -> {
            if (mMediaPlayer != null) {
                mMediaPlayer.start();
                updatePlayback();
            }
        });
        mMediaPlayer.setOnErrorListener((mp, what, extra) -> {
            mMainHandler.post(() -> Toast.makeText(
                    this, R.string.audio_player_error, Toast.LENGTH_SHORT
            ).show());
            exit();
            return true;
        });
        mMediaPlayer.setOnMediaTimeDiscontinuityListener((mp, mts) -> updatePlayback());
        mMediaPlayer.setOnSeekCompleteListener(mp -> updatePlayback());
        mMediaPlayer.setOnCompletionListener(mp -> updatePlayback());

        // Create floating window
        final Context context = new ContextThemeWrapper(this, R.style.Theme_AudioPlayer);
        mFloatingWindow = new FloatingWindow(context);
        mFloatingWindow.setOnSettingsViewClickListener(v -> goToSettings());
        mFloatingWindow.setOnCloseViewClickListener(v -> exit());
        mFloatingWindow.setOnStateViewClickListener(v -> {
            if (mMediaPlayer != null) {
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.pause();
                } else {
                    mMediaPlayer.start();
                }
                updatePlayback();
            }
        });
        mFloatingWindow.setOnLoopViewClickListener(v -> {
            if (mMediaPlayer != null) {
                mMediaPlayer.setLooping(!mMediaPlayer.isLooping());
                updatePlayback();
            }
        });
        mFloatingWindow.setOnTimelineViewTouchListener(new Slider.OnSliderTouchListener() {
            @SuppressLint("RestrictedApi")
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
            }

            @SuppressLint("RestrictedApi")
            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                if (mMediaPlayer != null) {
                    mMediaPlayer.seekTo((int) slider.getValue());
                    updatePlayback();
                }
            }
        });

        // Create notification builder
        final PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE
        );
        mNotificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(contentIntent);

        // Create notification channel
        final NotificationChannelCompat channel = new NotificationChannelCompat.Builder(
                NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT
        ).setName(getString(R.string.audio_player_notification_channel_name)).build();
        NotificationManagerCompat.from(this).createNotificationChannel(channel);

        // Register preference change listener
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mSharedPreferences.registerOnSharedPreferenceChangeListener(mOnPreferenceChangeListener);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        final Uri uri = intent.getData();
        if (uri == null) {
            return START_NOT_STICKY;
        }

        // Set data source to media player
        mMediaPlayer.reset();
        try {
            mMediaPlayer.setDataSource(this, uri);
        } catch (IOException e) {
            Toast.makeText(this, R.string.audio_player_error, Toast.LENGTH_SHORT).show();
            return START_NOT_STICKY;
        }

        // Extract media metadata
        final String title;
        final String subtitle;
        final Bitmap artwork;
        try (final MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(this, uri);
            final String mediaTitle = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_TITLE
            );
            title = mediaTitle == null ? getFileDisplayNameFromUri(uri) : mediaTitle;
            subtitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            final byte[] raw = retriever.getEmbeddedPicture();
            if (raw == null) {
                final Drawable drawable = DrawableCompat.wrap(
                        ContextCompat.getDrawable(this, R.drawable.ic_artwork)
                ).mutate();
                drawable.setTint(ContextCompat.getColor(this, R.color.tertiary_text_light));
                artwork = convertDrawableToBitmap(drawable);
            } else {
                artwork = BitmapFactory.decodeByteArray(raw, 0, raw.length);
            }
        }

        // Set metadata to floating window
        mFloatingWindow.setTitle(title);
        mFloatingWindow.setSubtitle(subtitle);
        mFloatingWindow.setArtwork(artwork);

        // Set metadata to notification builder
        mNotificationBuilder.setContentTitle(title)
                .setContentText(subtitle)
                .setLargeIcon(artwork);

        // Create and post notification
        final Notification notification = mNotificationBuilder
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle())
                .build();
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        startForeground(NOTIFICATION_ID, notification);

        // Attach floating window
        if (mSharedPreferences.getBoolean("floating_window", false)
                && Settings.canDrawOverlays(this)) {
            mFloatingWindow.attach();
        }

        // Prepare media player
        mMediaPlayer.prepareAsync();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Terminate scheduled executor
        mScheduledExecutorService.shutdown();

        // Unregister broadcast receiver
        unregisterReceiver(mBroadcastReceiver);

        // Unregister preference change listener
        if (mSharedPreferences != null) {
            mSharedPreferences.unregisterOnSharedPreferenceChangeListener(
                    mOnPreferenceChangeListener
            );
            mSharedPreferences = null;
        }

        // Detach floating window
        if (mFloatingWindow != null) {
            mFloatingWindow.detach();
            mFloatingWindow = null;
        }

        // Release media player
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @AnyThread
    private void updatePlayback() {
        if (mMediaPlayer == null) {
            return;
        }

        final boolean isPlaying = mMediaPlayer.isPlaying();
        final boolean isLooping = mMediaPlayer.isLooping();
        final int currentPosition = mMediaPlayer.getCurrentPosition();
        final int duration = mMediaPlayer.getDuration();

        if (isPlaying) {
            schedulePlaybackPositionUpdateTask();
        } else {
            cancelPlaybackPositionUpdateTask();
        }

        if (mFloatingWindow != null) {
            mFloatingWindow.setPlaying(isPlaying);
            mFloatingWindow.setLooping(isLooping);
            mFloatingWindow.setTimeLine(currentPosition, duration);
        }

        if (mNotificationBuilder != null) {
            final Notification notification = mNotificationBuilder.clearActions()
                    .addAction(
                            isPlaying ? R.drawable.ic_pause : R.drawable.ic_play,
                            isPlaying ? "Pause" : "Play",
                            createActionPendingIntent(INTENT_ACTION_STATE)
                    )
                    .addAction(
                            isLooping ? R.drawable.ic_loop_on : R.drawable.ic_loop_off,
                            isLooping ? "No loop" : "Loop",
                            createActionPendingIntent(INTENT_ACTION_LOOP)
                    )
                    .addAction(
                            R.drawable.ic_close_outlined,
                            "Exit",
                            createActionPendingIntent(INTENT_ACTION_EXIT)
                    )
                    .setStyle(
                            new androidx.media.app.NotificationCompat.MediaStyle()
                                    .setShowActionsInCompactView(0, 1, 2)
                    ).build();
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void schedulePlaybackPositionUpdateTask() {
        if (mPlaybackPositionUpdateTask != null) {
            return;
        }
        synchronized (mLock) {
            if (mPlaybackPositionUpdateTask != null) {
                return;
            }
            mPlaybackPositionUpdateTask = mScheduledExecutorService.scheduleAtFixedRate(() -> {
                if (mMediaPlayer != null && mFloatingWindow != null) {
                    mFloatingWindow.setTimeLine(
                            mMediaPlayer.getCurrentPosition(), mMediaPlayer.getDuration()
                    );
                }
            }, 50L, 50L, TimeUnit.MILLISECONDS);
            mPlaybackPositionUpdateTask = null;
        }
    }

    private void cancelPlaybackPositionUpdateTask() {
        if (mPlaybackPositionUpdateTask == null) {
            return;
        }
        synchronized (mLock) {
            if (mPlaybackPositionUpdateTask == null) {
                return;
            }
            mPlaybackPositionUpdateTask.cancel(false);
            mPlaybackPositionUpdateTask = null;
        }
    }

    @NonNull
    private PendingIntent createActionPendingIntent(@NonNull final String action) {
        return PendingIntent.getBroadcast(
                this,
                0,
                new Intent(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    @SuppressLint("Range")
    private String getFileDisplayNameFromUri(Uri uri) {
        final String scheme = uri.getScheme();

        if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            return uri.getLastPathSegment();
        } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            final String[] projection = {OpenableColumns.DISPLAY_NAME};
            try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.getCount() != 0) {
                    cursor.moveToFirst();
                    return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }

        return uri.toString();
    }

    @NonNull
    private Bitmap convertDrawableToBitmap(@NonNull final Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            final BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        final Bitmap bitmap;
        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        } else {
            bitmap = Bitmap.createBitmap(
                    drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(),
                    Bitmap.Config.ARGB_8888
            );
        }

        final Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    private void goToSettings() {
        final Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void exit() {
        stopService(new Intent(this, AudioPlayerService.class));
    }

    private static class FloatingWindow extends CardView {
        private CharSequence mTitle;

        private CharSequence mSubtitle;

        private Bitmap mArtwork;

        private boolean mIsPlaying;

        private boolean mIsLooping;

        private long mCurrentPosition;

        private long mDuration;

        private final AppCompatImageView mSettingsView;

        private final AppCompatImageView mLockView;

        private final AppCompatImageView mCloseView;

        private final AppCompatImageView mArtworkView;

        private final TextView mTitleView;

        private final TextView mSubtitleView;

        private final AppCompatImageView mStateView;

        private final AppCompatImageView mLoopView;

        private final TextView mCurrentPositionView;

        private final TextView mDurationView;

        private final Slider mTimelineView;

        private final WindowManager mWindowManager;

        private Slider.OnSliderTouchListener mOnTimelineViewTouchListener;

        private boolean mIsTimelineTouching;

        public FloatingWindow(@NonNull Context context) {
            super(context);

            setRadius(dipToPx(12f));
            setCardElevation(dipToPx(2f));
            setPreventCornerOverlap(true);
            setUseCompatPadding(true);

            inflate(context, R.layout.floating_window, this);
            mSettingsView = findViewById(R.id.settings);
            mLockView = findViewById(R.id.lock);
            mCloseView = findViewById(R.id.close);
            mArtworkView = findViewById(R.id.artwork);
            mTitleView = findViewById(R.id.title);
            mSubtitleView = findViewById(R.id.subtitle);
            mStateView = findViewById(R.id.state);
            mLoopView = findViewById(R.id.loop);
            mCurrentPositionView = findViewById(R.id.current_position);
            mDurationView = findViewById(R.id.duration);
            mTimelineView = findViewById(R.id.timeline);
            mWindowManager = context.getSystemService(WindowManager.class);

            final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            layoutParams.gravity = Gravity.START | Gravity.TOP;
            setLayoutParams(layoutParams);

            setOnTouchListener(new View.OnTouchListener() {
                private float mDiffX;
                private float mDiffY;

                @SuppressLint("ClickableViewAccessibility")
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (mLockView.isSelected()) {
                        return false;
                    }

                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            mDiffX = -event.getX();
                            mDiffY = -event.getY();
                            setCardElevation(dipToPx(8f));
                            break;
                        case MotionEvent.ACTION_UP:
                            setCardElevation(dipToPx(2f));
                            break;
                        case MotionEvent.ACTION_MOVE:
                            layoutParams.x = (int) (event.getRawX() + mDiffX);
                            layoutParams.y = (int) (event.getRawY() + mDiffY);
                            if (isAttachedToWindow()) {
                                mWindowManager.updateViewLayout(FloatingWindow.this, layoutParams);
                            }
                            break;
                    }
                    return true;
                }
            });

            mTitleView.setSelected(true);
            mSubtitleView.setSelected(true);
            mLockView.setOnClickListener(v -> mLockView.setSelected(!mLockView.isSelected()));
            mTimelineView.setLabelFormatter(value -> formatDuration((long) value));
            mTimelineView.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                @SuppressLint("RestrictedApi")
                @Override
                public void onStartTrackingTouch(@NonNull Slider slider) {
                    mIsTimelineTouching = true;
                }

                @SuppressLint("RestrictedApi")
                @Override
                public void onStopTrackingTouch(@NonNull Slider slider) {
                    mIsTimelineTouching = false;
                }
            });
        }

        @AnyThread
        public void setTitle(CharSequence title) {
            if (Objects.equals(mTitle, title)) {
                return;
            }
            mTitle = title;
            post(() -> mTitleView.setText(mTitle));
        }

        @AnyThread
        public void setSubtitle(CharSequence subtitle) {
            if (Objects.equals(mSubtitle, subtitle)) {
                return;
            }
            mSubtitle = subtitle;
            post(() -> mSubtitleView.setText(mSubtitle));
        }

        @AnyThread
        public void setArtwork(Bitmap artwork) {
            if (Objects.equals(mArtwork, artwork)) {
                return;
            }
            mArtwork = artwork;
            post(() -> mArtworkView.setImageBitmap(mArtwork));
        }

        @AnyThread
        public void setPlaying(boolean playing) {
            if (mIsPlaying == playing) {
                return;
            }
            mIsPlaying = playing;
            post(() -> mStateView.setSelected(mIsPlaying));
        }

        @AnyThread
        public void setLooping(boolean looping) {
            if (mIsLooping == looping) {
                return;
            }
            mIsLooping = looping;
            post(() -> mLoopView.setSelected(mIsLooping));
        }

        @AnyThread
        public void setTimeLine(long currentPosition, long duration) {
            if (mCurrentPosition == currentPosition && mDuration == duration) {
                return;
            }
            mDuration = Math.max(0L, duration);
            mCurrentPosition = Math.min(Math.max(0L, currentPosition), mDuration);
            post(() -> {
                if (!mIsTimelineTouching || mDuration != (long) mTimelineView.getValueTo()) {
                    mTimelineView.setValueFrom(0L);
                    if (mDuration <= 0L) {
                        mTimelineView.setValueTo(Long.MAX_VALUE);
                        mTimelineView.setEnabled(false);
                    } else {
                        mTimelineView.setValueTo(mDuration);
                        mTimelineView.setEnabled(true);
                    }
                    mTimelineView.setValue(mCurrentPosition);
                }

                mCurrentPositionView.setText(formatDuration(mCurrentPosition));
                mDurationView.setText(formatDuration(mDuration));
            });
        }

        public void setOnSettingsViewClickListener(@Nullable final OnClickListener listener) {
            mSettingsView.setOnClickListener(listener);
        }

        public void setOnCloseViewClickListener(@Nullable final OnClickListener listener) {
            mCloseView.setOnClickListener(listener);
        }

        public void setOnStateViewClickListener(@Nullable final OnClickListener listener) {
            mStateView.setOnClickListener(listener);
        }

        public void setOnLoopViewClickListener(@Nullable final OnClickListener listener) {
            mLoopView.setOnClickListener(listener);
        }

        public void setOnTimelineViewTouchListener(
                @Nullable final Slider.OnSliderTouchListener listener) {
            if (mOnTimelineViewTouchListener != null) {
                mTimelineView.removeOnSliderTouchListener(mOnTimelineViewTouchListener);
            }

            mOnTimelineViewTouchListener = listener;
            if (mOnTimelineViewTouchListener != null) {
                mTimelineView.addOnSliderTouchListener(mOnTimelineViewTouchListener);
            }
        }

        public void attach() {
            if (!isAttachedToWindow()) {
                mWindowManager.addView(this, getLayoutParams());
            }
        }

        public void detach() {
            if (isAttachedToWindow()) {
                mWindowManager.removeView(this);
            }
        }

        @SuppressLint("DefaultLocale")
        @NonNull
        private String formatDuration(final long duration) {
            return String.format("%d:%02d", duration / 1000 / 60, (duration / 1000) % 60);
        }

        private float dipToPx(final float dip) {
            return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, dip, getResources().getDisplayMetrics()
            );
        }
    }
}
