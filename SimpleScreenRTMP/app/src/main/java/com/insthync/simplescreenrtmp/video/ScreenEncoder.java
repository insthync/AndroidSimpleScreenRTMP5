package com.insthync.simplescreenrtmp.video;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetH264Data;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Ittipon on 6/26/17.
 */

public class ScreenEncoder {

    private String TAG = "ScreenEncoder";
    private MediaCodec videoEncoder;
    private Thread thread;
    private GetH264Data getH264Data;
    private MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
    private long mPresentTimeUs;
    private boolean running = false;
    private boolean spsPpsSetted = false;

    //surface to buffer encoder
    private Surface inputSurface;

    //default parameters for encoder
    private String mime = "video/avc";
    private int width = 640;
    private int height = 480;
    private int fps = 30;
    private int bitRate = 1200 * 1024; //in kbps
    private FormatVideoEncoder formatVideoEncoder = FormatVideoEncoder.YUV420Dynamical;

    public ScreenEncoder(GetH264Data getH264Data) {
        this.getH264Data = getH264Data;
    }

    /**
     * Prepare encoder with custom parameters
     */
    public boolean prepareVideoEncoder(int width, int height, int fps, int bitRate, FormatVideoEncoder formatVideoEncoder) {
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.bitRate = bitRate;
        this.formatVideoEncoder = formatVideoEncoder;
        MediaCodecInfo encoder;
        if (Build.VERSION.SDK_INT >= 21) {
            encoder = chooseVideoEncoderAPI21(mime);
        } else {
            encoder = chooseVideoEncoder(mime);
        }
        try {
            if (encoder != null) {
                videoEncoder = MediaCodec.createByCodecName(encoder.getName());
                if (this.formatVideoEncoder == FormatVideoEncoder.YUV420Dynamical) {
                    this.formatVideoEncoder = chooseColorDynamically(encoder);
                    if (this.formatVideoEncoder == null) {
                        Log.e(TAG, "YUV420 dynamical choose failed");
                        return false;
                    }
                }
            } else {
                Log.e(TAG, "valid encoder not found");
                return false;
            }

            MediaFormat videoFormat;
            videoFormat = MediaFormat.createVideoFormat(mime, width, height);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    this.formatVideoEncoder.getFormatCodec());
            videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            running = false;
            if (formatVideoEncoder == FormatVideoEncoder.SURFACE
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                inputSurface = videoEncoder.createInputSurface();
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "create videoEncoder failed.");
            e.printStackTrace();
            return false;
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return false;
        }
    }

    private FormatVideoEncoder chooseColorDynamically(MediaCodecInfo mediaCodecInfo) {
        for (int color : mediaCodecInfo.getCapabilitiesForType(mime).colorFormats) {
            if (color == FormatVideoEncoder.YUV420PLANAR.getFormatCodec()) {
                return FormatVideoEncoder.YUV420PLANAR;
            } else if (color == FormatVideoEncoder.YUV420SEMIPLANAR.getFormatCodec()) {
                return FormatVideoEncoder.YUV420SEMIPLANAR;
            } else if (color == FormatVideoEncoder.YUV420PACKEDPLANAR.getFormatCodec()) {
                return FormatVideoEncoder.YUV420PACKEDPLANAR;
            }
        }
        return null;
    }

    /**
     * Prepare encoder with default parameters
     */
    public boolean prepareVideoEncoder() {
        return prepareVideoEncoder(width, height, fps, bitRate, formatVideoEncoder);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void setVideoBitrateOnFly(int bitrate) {
        if (isRunning()) {
            this.bitRate = bitrate;
            Bundle bundle = new Bundle();
            bundle.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate);
            try {
                videoEncoder.setParameters(bundle);
            } catch (IllegalStateException e) {
                Log.e(TAG, "encoder need be running");
                e.printStackTrace();
            }
        }
    }

    public Surface getInputSurface() {
        return inputSurface;
    }

    public void setInputSurface(Surface inputSurface) {
        this.inputSurface = inputSurface;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        spsPpsSetted = false;
        mPresentTimeUs = System.nanoTime() / 1000;
        videoEncoder.start();
        //surface to buffer
        if (formatVideoEncoder == FormatVideoEncoder.SURFACE
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if (Build.VERSION.SDK_INT >= 21) {
                getDataFromSurfaceAPI21();
            } else {
                getDataFromSurface();
            }
            //buffer to buffer
        } else {
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE);
                    while (!Thread.interrupted()) {
                        if (Build.VERSION.SDK_INT >= 21) {
                            getDataFromEncoderAPI21();
                        } else {
                            getDataFromEncoder();
                        }
                    }
                }
            });
            thread.start();
        }
        running = true;
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException e) {
                thread.interrupt();
            }
            thread = null;
        }
        if (videoEncoder != null) {
            videoEncoder.stop();
            videoEncoder.release();
            videoEncoder = null;
        }
        spsPpsSetted = false;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void getDataFromSurfaceAPI21() {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.interrupted()) {
                    for (; ; ) {
                        int outBufferIndex = videoEncoder.dequeueOutputBuffer(videoInfo, 0);
                        if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            MediaFormat mediaFormat = videoEncoder.getOutputFormat();
                            getH264Data.onSPSandPPS(mediaFormat.getByteBuffer("csd-0"),
                                    mediaFormat.getByteBuffer("csd-1"));
                            spsPpsSetted = true;
                        } else if (outBufferIndex >= 0) {
                            if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                if (!spsPpsSetted) {
                                    Pair<ByteBuffer, ByteBuffer> buffers =
                                            decodeSpsPpsFromBuffer(videoEncoder.getOutputBuffer(outBufferIndex),
                                                    videoInfo.size);
                                    if (buffers != null) {
                                        getH264Data.onSPSandPPS(buffers.first, buffers.second);
                                        spsPpsSetted = true;
                                    }
                                }
                            } else {
                                //This ByteBuffer is H264
                                ByteBuffer bb = videoEncoder.getOutputBuffer(outBufferIndex);
                                getH264Data.getH264Data(bb, videoInfo);
                                videoEncoder.releaseOutputBuffer(outBufferIndex, false);
                            }
                        } else {
                            break;
                        }
                    }
                }
            }
        });
        thread.start();
    }

    private void getDataFromSurface() {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.interrupted()) {
                    ByteBuffer[] outputBuffers = videoEncoder.getOutputBuffers();
                    for (; ; ) {
                        int outBufferIndex = videoEncoder.dequeueOutputBuffer(videoInfo, 0);
                        if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            MediaFormat mediaFormat = videoEncoder.getOutputFormat();
                            getH264Data.onSPSandPPS(mediaFormat.getByteBuffer("csd-0"),
                                    mediaFormat.getByteBuffer("csd-1"));
                            spsPpsSetted = true;
                        } else if (outBufferIndex >= 0) {
                            if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                if (!spsPpsSetted) {
                                    Pair<ByteBuffer, ByteBuffer> buffers =
                                            decodeSpsPpsFromBuffer(outputBuffers[outBufferIndex], videoInfo.size);
                                    if (buffers != null) {
                                        getH264Data.onSPSandPPS(buffers.first, buffers.second);
                                        spsPpsSetted = true;
                                    }
                                }
                            } else {
                                //This ByteBuffer is H264
                                ByteBuffer bb = outputBuffers[outBufferIndex];
                                getH264Data.getH264Data(bb, videoInfo);
                                videoEncoder.releaseOutputBuffer(outBufferIndex, false);
                            }
                        } else {
                            break;
                        }
                    }
                }
            }
        });
        thread.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void getDataFromEncoderAPI21() {
        for (; ; ) {
            int outBufferIndex = videoEncoder.dequeueOutputBuffer(videoInfo, 0);
            if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat mediaFormat = videoEncoder.getOutputFormat();
                getH264Data.onSPSandPPS(mediaFormat.getByteBuffer("csd-0"),
                        mediaFormat.getByteBuffer("csd-1"));
                spsPpsSetted = true;
            } else if (outBufferIndex >= 0) {
                if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    if (!spsPpsSetted) {
                        Pair<ByteBuffer, ByteBuffer> buffers =
                                decodeSpsPpsFromBuffer(videoEncoder.getOutputBuffer(outBufferIndex),
                                        videoInfo.size);
                        if (buffers != null) {
                            getH264Data.onSPSandPPS(buffers.first, buffers.second);
                            spsPpsSetted = true;
                        }
                    }
                } else {
                    //This ByteBuffer is H264
                    ByteBuffer bb = videoEncoder.getOutputBuffer(outBufferIndex);
                    getH264Data.getH264Data(bb, videoInfo);
                    videoEncoder.releaseOutputBuffer(outBufferIndex, false);
                }
            } else {
                break;
            }
        }
    }

    private void getDataFromEncoder() {
        ByteBuffer[] outputBuffers = videoEncoder.getOutputBuffers();

        for (; ; ) {
            int outBufferIndex = videoEncoder.dequeueOutputBuffer(videoInfo, 0);
            if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat mediaFormat = videoEncoder.getOutputFormat();
                getH264Data.onSPSandPPS(mediaFormat.getByteBuffer("csd-0"),
                        mediaFormat.getByteBuffer("csd-1"));
                spsPpsSetted = true;
            } else if (outBufferIndex >= 0) {
                if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    if (!spsPpsSetted) {
                        Pair<ByteBuffer, ByteBuffer> buffers =
                                decodeSpsPpsFromBuffer(outputBuffers[outBufferIndex], videoInfo.size);
                        if (buffers != null) {
                            getH264Data.onSPSandPPS(buffers.first, buffers.second);
                            spsPpsSetted = true;
                        }
                    }
                } else {
                    //This ByteBuffer is H264
                    ByteBuffer bb = outputBuffers[outBufferIndex];
                    getH264Data.getH264Data(bb, videoInfo);
                    videoEncoder.releaseOutputBuffer(outBufferIndex, false);
                }
            } else {
                break;
            }
        }
    }

    /**
     * choose the video encoder by mime. API 21+
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private MediaCodecInfo chooseVideoEncoderAPI21(String mime) {
        MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        MediaCodecInfo[] mediaCodecInfos = mediaCodecList.getCodecInfos();
        for (MediaCodecInfo mci : mediaCodecInfos) {
            if (!mci.isEncoder()) {
                continue;
            }
            String[] types = mci.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mime)) {
                    Log.i(TAG, String.format("videoEncoder %s type supported: %s", mci.getName(), type));
                    MediaCodecInfo.CodecCapabilities codecCapabilities = mci.getCapabilitiesForType(mime);
                    for (int color : codecCapabilities.colorFormats) {
                        Log.i(TAG, "Color supported: " + color);
                        //check if encoder support any yuv420 color
                        if (color == FormatVideoEncoder.YUV420PLANAR.getFormatCodec()
                                || color == FormatVideoEncoder.YUV420SEMIPLANAR.getFormatCodec()
                                || color == FormatVideoEncoder.YUV420PACKEDPLANAR.getFormatCodec()) {
                            return mci;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * choose the video encoder by mime. API < 21
     */
    private MediaCodecInfo chooseVideoEncoder(String mime) {
        int count = MediaCodecList.getCodecCount();
        for (int i = 0; i < count; i++) {
            MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
            if (!mci.isEncoder()) {
                continue;
            }
            String[] types = mci.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mime)) {
                    Log.i(TAG, String.format("videoEncoder %s type supported: %s", mci.getName(), type));
                    MediaCodecInfo.CodecCapabilities codecCapabilities = mci.getCapabilitiesForType(mime);
                    for (int color : codecCapabilities.colorFormats) {
                        Log.i(TAG, "Color supported: " + color);
                        //check if encoder support any yuv420 color
                        if (color == FormatVideoEncoder.YUV420PLANAR.getFormatCodec()
                                || color == FormatVideoEncoder.YUV420SEMIPLANAR.getFormatCodec()
                                || color == FormatVideoEncoder.YUV420PACKEDPLANAR.getFormatCodec()) {
                            return mci;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * decode sps and pps if the encoder never call to MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
     */
    private Pair<ByteBuffer, ByteBuffer> decodeSpsPpsFromBuffer(ByteBuffer outputBuffer, int length) {
        byte[] mSPS = null, mPPS = null;
        byte[] csd = new byte[length];
        outputBuffer.get(csd, 0, length);
        int i = 0;
        int spsIndex = -1;
        int ppsIndex = -1;
        while (i < length - 4) {
            if (csd[i] == 0 && csd[i + 1] == 0 && csd[i + 2] == 0 && csd[i + 3] == 1) {
                if (spsIndex == -1) {
                    spsIndex = i;
                } else {
                    ppsIndex = i;
                    break;
                }
            }
            i++;
        }
        if (spsIndex != -1 && ppsIndex != -1) {
            mSPS = new byte[ppsIndex];
            System.arraycopy(csd, spsIndex, mSPS, 0, ppsIndex);
            mPPS = new byte[length - ppsIndex];
            System.arraycopy(csd, ppsIndex, mPPS, 0, length - ppsIndex);
        }
        if (mSPS != null && mPPS != null) {
            return new Pair<>(ByteBuffer.wrap(mSPS), ByteBuffer.wrap(mPPS));
        }
        return null;
    }
}