package com.pedro.encoder.audio;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 19/01/17.
 * Encode PCM audio data to ACC and return in a callback
 */

public class AudioEncoder implements GetMicrophoneData {

  private String TAG = "AudioEncoder";
  private MediaCodec audioEncoder;
  private GetAccData getAccData;
  private MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();
  private long mPresentTimeUs;
  private boolean running;

  //default parameters for encoder
  private String mime = "audio/mp4a-latm";
  private int bitRate = 128 * 1024;  //in kbps
  private int sampleRate = 44100; //in hz
  private boolean isStereo = true;

  public AudioEncoder(GetAccData getAccData) {
    this.getAccData = getAccData;
  }

  /**
   * Prepare encoder with custom parameters
   */
  public boolean prepareAudioEncoder(int bitRate, int sampleRate, boolean isStereo) {
    this.sampleRate = sampleRate;
    try {
      audioEncoder = MediaCodec.createEncoderByType(mime);
      int a = (isStereo) ? 2 : 1;
      MediaFormat audioFormat = MediaFormat.createAudioFormat(mime, sampleRate, a);
      audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
      audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MicrophoneManager.BUFFER_SIZE);
      audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
          MediaCodecInfo.CodecProfileLevel.AACObjectLC);
      audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      running = false;
      return true;
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    } catch (IllegalStateException e) {
      e.printStackTrace();
      return false;
    }
  }

  /**
   * Prepare encoder with default parameters
   */
  public boolean prepareAudioEncoder() {
    return prepareAudioEncoder(bitRate, sampleRate, isStereo);
  }

  public void start() {
    mPresentTimeUs = System.nanoTime() / 1000;
    audioEncoder.start();
    running = true;
    Log.i(TAG, "AudioEncoder started");
  }

  public void stop() {
    running = false;
    if (audioEncoder != null) {
      audioEncoder.stop();
      audioEncoder.release();
      audioEncoder = null;
    }
    Log.i(TAG, "AudioEncoder stopped");
  }

  /**
   * Set custom PCM data.
   * Use it after prepareAudioEncoder(int sampleRate, int channel).
   * Used too with microphone.
   *
   * @param buffer PCM buffer
   * @param size Min PCM buffer size
   */
  @Override
  public void inputPcmData(final byte[] buffer, final int size) {
    if (Build.VERSION.SDK_INT >= 21) {
      getDataFromEncoderAPI21(buffer, size);
    } else {
      getDataFromEncoder(buffer, size);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private void getDataFromEncoderAPI21(byte[] data, int size) {
    int inBufferIndex = audioEncoder.dequeueInputBuffer(-1);
    if (inBufferIndex >= 0) {
      ByteBuffer bb = audioEncoder.getInputBuffer(inBufferIndex);
      bb.put(data, 0, size);
      long pts = System.nanoTime() / 1000 - mPresentTimeUs;
      audioEncoder.queueInputBuffer(inBufferIndex, 0, size, pts, 0);
    }

    for (; ; ) {
      int outBufferIndex = audioEncoder.dequeueOutputBuffer(audioInfo, 0);
      if (outBufferIndex >= 0) {
        //This ByteBuffer is ACC
        ByteBuffer bb = audioEncoder.getOutputBuffer(outBufferIndex);
        getAccData.getAccData(bb, audioInfo);
        audioEncoder.releaseOutputBuffer(outBufferIndex, false);
      } else {
        break;
      }
    }
  }

  private void getDataFromEncoder(byte[] data, int size) {
    ByteBuffer[] inputBuffers = audioEncoder.getInputBuffers();
    ByteBuffer[] outputBuffers = audioEncoder.getOutputBuffers();

    int inBufferIndex = audioEncoder.dequeueInputBuffer(-1);
    if (inBufferIndex >= 0) {
      ByteBuffer bb = inputBuffers[inBufferIndex];
      bb.clear();
      bb.put(data, 0, size);
      long pts = System.nanoTime() / 1000 - mPresentTimeUs;
      audioEncoder.queueInputBuffer(inBufferIndex, 0, size, pts, 0);
    }

    for (; ; ) {
      int outBufferIndex = audioEncoder.dequeueOutputBuffer(audioInfo, 0);
      if (outBufferIndex >= 0) {
        //This ByteBuffer is ACC
        ByteBuffer bb = outputBuffers[outBufferIndex];
        getAccData.getAccData(bb, audioInfo);
        audioEncoder.releaseOutputBuffer(outBufferIndex, false);
      } else {
        break;
      }
    }
  }

  public void setSampleRate(int sampleRate) {
    this.sampleRate = sampleRate;
  }

  public boolean isRunning() {
    return running;
  }
}
