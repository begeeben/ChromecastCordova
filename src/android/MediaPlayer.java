package com.sesamtv.cordova.chromecast;

import android.net.Uri;
import android.util.Log;
import com.google.android.gms.cast.*;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.images.WebImage;

import com.google.android.gms.drive.Metadata;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;


public class MediaPlayer {
    private RemoteMediaPlayer mMediaPlayer;
    private static final String TAG = "ChromeCastPluginMediaPlayer";
    private GoogleApiClient mApiClient;
    private com.sesamtv.cordova.chromecast.ChromeCast parentContext;
    private Boolean mSeeking = false;

    public MediaPlayer(GoogleApiClient client, com.sesamtv.cordova.chromecast.ChromeCast scope) {
        mApiClient = client;
        parentContext = scope;
    }


    public void attachMediaPlayer() {
        if (mMediaPlayer != null) {
            return;
        }

        mMediaPlayer = new RemoteMediaPlayer();
        mMediaPlayer.setOnStatusUpdatedListener(new RemoteMediaPlayer.OnStatusUpdatedListener() {
            @Override
            public void onStatusUpdated() {
                Log.d(TAG, "MediaControlChannel.onStatusUpdated");
                // If item has ended, clear metadata.
                MediaStatus mediaStatus = mMediaPlayer.getMediaStatus();
                if ((mediaStatus != null)
                        && (mediaStatus.getPlayerState() == MediaStatus.PLAYER_STATE_IDLE)) {
                    //todo clear states
                    Log.d(TAG, "clear states");
                }
                parentContext.onMediaStatusCallback(getMediaStatus());
            }
        });

        mMediaPlayer.setOnMetadataUpdatedListener(
                new RemoteMediaPlayer.OnMetadataUpdatedListener() {
                    @Override
                    public void onMetadataUpdated() {
                        Log.d(TAG, "MediaControlChannel.onMetadataUpdated");

                        //todo update metadata
                    }
                }
        );

        try {
            Cast.CastApi.setMessageReceivedCallbacks(mApiClient, mMediaPlayer.getNamespace(),
                    mMediaPlayer);
        } catch (IOException e) {
            Log.w(TAG, "Exception while launching application", e);
        }
    }

    public void reattachMediaPlayer() {
        if ((mMediaPlayer != null) && (mApiClient != null)) {
            try {
                Cast.CastApi.setMessageReceivedCallbacks(mApiClient, mMediaPlayer.getNamespace(),
                        mMediaPlayer);
            } catch (IOException e) {
                Log.w(TAG, "Exception while launching application", e);
            }
        }
    }

    public void detachMediaPlayer() {
        if ((mMediaPlayer != null) && (mApiClient != null)) {
            try {
                Cast.CastApi.removeMessageReceivedCallbacks(mApiClient,
                        mMediaPlayer.getNamespace());
            } catch (IOException e) {
                Log.w(TAG, "Exception while launching application", e);
            }
        }
        mMediaPlayer = null;
    }

    public JSONObject getMediaStatus() {
        JSONObject status = new JSONObject();

        try {
            status.put("deviceVolume", Cast.CastApi.getVolume(mApiClient));
            status.put("deviceMuted", Cast.CastApi.isMute(mApiClient));
            if (mMediaPlayer != null) {
                MediaMetadata metadata = null;
                MediaStatus mediaStatus = mMediaPlayer.getMediaStatus();
                status.put("duration", mMediaPlayer.getStreamDuration());
                if (mediaStatus != null) {
                    MediaInfo mediaInfo = mediaStatus.getMediaInfo();
                    if (mediaInfo != null) {
                        metadata = mediaInfo.getMetadata();
                    }
                    status.put("state", mediaStatus.getPlayerState());
                    status.put("position", mMediaPlayer.getApproximateStreamPosition());
                    status.put("idleReason", mediaStatus.getIdleReason());
                    status.put("contentId", mediaStatus.getMediaInfo().getContentId());
                    status.put("contentType", mediaStatus.getMediaInfo().getContentType());
                    status.put("volume", mediaStatus.getStreamVolume());
                    status.put("muted", mediaStatus.isMute());
                    status.put("customData", mediaStatus.getCustomData());
                }
                if (metadata != null) {
                    status.put("title", metadata.getString(MediaMetadata.KEY_TITLE));
                    String artist = metadata.getString(MediaMetadata.KEY_ARTIST);
                    if (artist != null) {
                        status.put("artist", artist);
                        status.put("studio", metadata.getString(MediaMetadata.KEY_STUDIO));
                    }
                    List<WebImage> images = metadata.getImages();
                    if ((images != null) && !images.isEmpty()) {
                        WebImage image = images.get(0);
                        status.put("imageUrl", image.getUrl());
                    }
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return status;
    }

    /*
     * Begins playback of the currently selected video.
     */
    public void loadMedia(JSONObject media, final CallbackContext callbackContext) {
        Log.d(TAG, "loadMedia: " + media);
        if (media == null) {
            Log.e(TAG, "loadMedia: no media found");
            callbackContext.error("no media found");
            return;
        }

        if (mMediaPlayer == null) {
            Log.e(TAG, "Trying to play a video with no active media session");
            callbackContext.error("Trying to play a video with no active media session");
            return;
        }
        try {
            boolean isAutoPlay = media.has("autoplay") && media.getBoolean("autoplay");
            MediaInfo mediaInfo = buildMediaInfo(media.getJSONObject("mediaInfo"));

            MediaInfo currentInfo = mMediaPlayer.getMediaInfo();
            if (currentInfo != null && currentInfo.getContentId().equals(mediaInfo.getContentId())) {
                Log.d(TAG, "loadMedia: media is the same");
                if (isAutoPlay) {
                    this.playMedia(callbackContext);
                }

            } else {
                mMediaPlayer.load(mApiClient, mediaInfo, isAutoPlay)
                        .setResultCallback(
                                new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                                    @Override
                                    public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                                        if (!result.getStatus().isSuccess()) {
                                            Log.e(TAG, "Failed to load media.");
                                            callbackContext.error("Failed to load media.");
                                        } else {
                                            callbackContext.success();
                                        }
                                    }
                                }
                        );
            }

        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

    }

    public void playMedia(CallbackContext callbackContext) {
        if (mMediaPlayer == null) {
            callbackContext.error("player not found");
            return;
        }
        try {
            mMediaPlayer.play(mApiClient);
            if (callbackContext != null) {
                callbackContext.success();
            }

        } catch (IOException e) {
            Log.e(TAG, "Unable to play", e);
            if (callbackContext != null) {
                callbackContext.error("unable to play");
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, e.getMessage());
            if (callbackContext != null) {
                callbackContext.error(e.getMessage());
            }
        }
    }

    public void pauseMedia(CallbackContext callbackContext) {
        if (mMediaPlayer == null) {
            callbackContext.error("player not found");
            return;
        }
        try {
            MediaStatus mediaStatus = mMediaPlayer.getMediaStatus();
            int playerState = mediaStatus.getPlayerState();

            if (mediaStatus != null && playerState != MediaStatus.PLAYER_STATE_PAUSED &&
                    playerState != MediaStatus.PLAYER_STATE_IDLE) {
                mMediaPlayer.pause(mApiClient);
            }

            callbackContext.success();
        } catch (IOException e) {
            Log.e(TAG, "Unable to pause", e);
            callbackContext.error("unable to pause");
        } catch (IllegalStateException e) {
            Log.e(TAG, e.getMessage());
            callbackContext.error(e.getMessage());
        }
    }

    public void stopMedia(CallbackContext callbackContext, JSONObject customData) {
        if (mMediaPlayer == null) {
            callbackContext.error("player not found");
            return;
        }
        try {
            MediaStatus mediaStatus = mMediaPlayer.getMediaStatus();
            if (mediaStatus != null && mediaStatus.getPlayerState() != MediaStatus.PLAYER_STATE_IDLE) {
                if (customData == null) {
                    mMediaPlayer.stop(mApiClient);
                } else {
                    mMediaPlayer.stop(mApiClient, customData);
                }
            }
            callbackContext.success();
        } catch (IOException e) {
            Log.e(TAG, "Unable to pause", e);
            callbackContext.error("unable to pause");
        } catch (IllegalStateException e) {
            Log.e(TAG, e.getMessage());
            callbackContext.error(e.getMessage());
        }
    }


    public void seekMedia(Long position, String... behavior) {

        if (mMediaPlayer == null) {
            return;
        }
        String afterSeekMode = "DO_NOTHING";
        if (behavior.length != 0) {
            afterSeekMode = behavior[0];
        }

        int resumeState = RemoteMediaPlayer.RESUME_STATE_UNCHANGED;
        if (afterSeekMode.equals("PLAY")) {
            resumeState = RemoteMediaPlayer.RESUME_STATE_PLAY;
        } else if (afterSeekMode.equals("PAUSE")) {
            resumeState = RemoteMediaPlayer.RESUME_STATE_PAUSE;
        } else if (afterSeekMode.equals("DO_NOTHING")) {
            resumeState = RemoteMediaPlayer.RESUME_STATE_UNCHANGED;
        }
        long duration = mMediaPlayer.getStreamDuration();
        if (position < 0) {
            position = 0L;
        } else if (position > duration) {
            position = duration;
        }
        Log.d(TAG, "seek media position " + position + " ;duration: " + duration);
        mSeeking = true;
        mMediaPlayer.seek(mApiClient, position, resumeState).setResultCallback(
                new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {

                    @Override
                    public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                        Status status = result.getStatus();
                        if (status.isSuccess()) {
                            mSeeking = false;
                        } else {
                            Log.e(TAG, "Unable to seek: " + status.getStatusCode());
                        }
                    }

                }
        );
    }

    public void seekMediaBy(long value, String... behavior) {
        long pos = mMediaPlayer.getApproximateStreamPosition() + value;
        this.seekMedia(pos, behavior);
    }

    public void setDeciveVolume(double volume) {
        if (mApiClient == null) {
            return;
        }
        try {
            Cast.CastApi.setVolume(mApiClient, volume);
        } catch (IOException e) {
            Log.e(TAG, "Unable to change volume");
        } catch (IllegalStateException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public void setDeviceVolumeBy(double value) {
        try {
            double vol = getMediaStatus().getDouble("deviceVolume") + value;
            if (vol < 0) {
                vol = 0;
            } else if (vol > 1) {
                vol = 1;
            }
            this.setDeciveVolume(vol);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public void toggleDeviceMuted() {
        if (mApiClient == null) {
            return;
        }
        this.setDeviceMuted(!Cast.CastApi.isMute(mApiClient));
    }

    public void setDeviceMuted(boolean muted) {
        if (mApiClient == null) {
            return;
        }
        try {
            Cast.CastApi.setMute(mApiClient, muted);
        } catch (IOException e) {
            Log.w(TAG, "Unable to toggle mute");
        } catch (IllegalStateException e) {
            Log.e(TAG, e.getMessage());
        }
    }


    private MediaInfo buildMediaInfo(JSONObject opt) {
        try {
            MediaInfo.Builder info = new MediaInfo.Builder(opt.getString("contentId"));
            info.setContentType(opt.getString("contentType"));
            if (opt.has("customData")) {
                info.setCustomData(opt.getJSONObject("customData"));
            }

            String streamType = opt.has("streamType") ? opt.getString("streamType") : "BUFFERED";

            if (streamType.equals("BUFFERED")) {
                info.setStreamType(MediaInfo.STREAM_TYPE_BUFFERED);
            } else if (streamType.equals("LIVE")) {
                info.setStreamType(MediaInfo.STREAM_TYPE_LIVE);
            } else if (streamType.equals("NONE")) {
                info.setStreamType(MediaInfo.STREAM_TYPE_NONE);
            }

            if (opt.has("duration")) {
                info.setStreamDuration(opt.getLong("duration"));
            }

            return info.build();

        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }
        return null;
    }

}
