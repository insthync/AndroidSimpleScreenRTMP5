package com.insthync.simplescreenrtmp.builder;

import android.media.MediaCodec;
import android.media.projection.MediaProjection;
import android.os.Build;

import com.insthync.simplescreenrtmp.video.ScreenEncoder;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAccData;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetH264Data;

import net.ossrs.rtmp.ConnectCheckerRtmp;
import net.ossrs.rtmp.SrsFlvMuxer;

import java.nio.ByteBuffer;

/**
 * Created by Ittipon on 6/26/17.
 */

public class ScreenRtmpBuilder implements GetAccData, GetH264Data, GetMicrophoneData {

    private int width;
    private int height;
    private MediaProjection mediaProjection;
    private ScreenEncoder screenEncoder;
    private MicrophoneManager microphoneManager;
    private AudioEncoder audioEncoder;
    private SrsFlvMuxer srsFlvMuxer;
    private boolean streaming;

    public ScreenRtmpBuilder(ConnectCheckerRtmp connectChecker) {
        screenEncoder = new ScreenEncoder(this);
        microphoneManager = new MicrophoneManager(this);
        audioEncoder = new AudioEncoder(this);
        srsFlvMuxer = new SrsFlvMuxer(connectChecker);
        streaming = false;
    }

    public void setAuthorization(String user, String password) {
        srsFlvMuxer.setAuthorization(user, password);
    }

    public boolean prepareVideo(int width, int height, int fps, int bitrate, boolean hardwareRotation, int rotation) {
        this.width = width;
        this.height = height;
        return screenEncoder.prepareVideoEncoder(width, height, fps, bitrate, FormatVideoEncoder.YUV420Dynamical);
    }

    public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
                                boolean noiseSuppressor) {
        microphoneManager.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor);
        srsFlvMuxer.setIsStereo(isStereo);
        srsFlvMuxer.setAsample_rate(sampleRate);
        return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo);
    }

    public boolean prepareVideo() {
        width = screenEncoder.getWidth();
        height = screenEncoder.getHeight();
        return screenEncoder.prepareVideoEncoder();
    }

    public boolean prepareAudio() {
        microphoneManager.createMicrophone();
        return audioEncoder.prepareAudioEncoder();
    }

    public void startStream(MediaProjection mediaProjection, String url) {
        this.mediaProjection = mediaProjection;
        srsFlvMuxer.start(url);
        srsFlvMuxer.setVideoResolution(width, height);
        screenEncoder.start();
        if (Build.VERSION.SDK_INT >= 21) {
            mediaProjection.createVirtualDisplay("Recording Display", width,
                    height, 320, 0 /* flags */, screenEncoder.getInputSurface(),
                    null /* callback */, null /* handler */);
        }
        audioEncoder.start();
        microphoneManager.start();
        streaming = true;
    }

    public void stopStream() {
        if (Build.VERSION.SDK_INT >= 21) {
            mediaProjection.stop();
        }
        microphoneManager.stop();
        srsFlvMuxer.stop();
        screenEncoder.stop();
        audioEncoder.stop();
        streaming = false;
    }

    public void disableAudio() {
        microphoneManager.mute();
    }

    public void enableAudio() {
        microphoneManager.unMute();
    }

    public boolean isAudioMuted() {
        return microphoneManager.isMuted();
    }

    /** need min API 19 */
    public void setVideoBitrateOnFly(int bitrate) {
        if (Build.VERSION.SDK_INT >= 19) {
            screenEncoder.setVideoBitrateOnFly(bitrate);
        }
    }

    public boolean isStreaming() {
        return streaming;
    }

    @Override
    public void getAccData(ByteBuffer accBuffer, MediaCodec.BufferInfo info) {
        srsFlvMuxer.sendAudio(accBuffer, info);
    }

    @Override
    public void onSPSandPPS(ByteBuffer sps, ByteBuffer pps) {
        srsFlvMuxer.setSpsPPs(sps, pps);
    }

    @Override
    public void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
        srsFlvMuxer.sendVideo(h264Buffer, info);
    }

    @Override
    public void inputPcmData(byte[] buffer, int size) {
        audioEncoder.inputPcmData(buffer, size);
    }
}
