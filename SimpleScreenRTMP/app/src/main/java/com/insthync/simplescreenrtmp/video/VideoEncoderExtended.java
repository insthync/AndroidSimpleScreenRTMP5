package com.insthync.simplescreenrtmp.video;

import android.hardware.display.VirtualDisplay;

import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetH264Data;
import com.pedro.encoder.video.VideoEncoder;

public class VideoEncoderExtended extends VideoEncoder {
    private VirtualDisplay virtualDisplay;
    public VideoEncoderExtended(GetH264Data getH264Data) {
        super(getH264Data);
    }

    public boolean prepareVideoEncoder(int width, int height, int fps, int bitRate, int rotation,
                                       boolean hardwareRotation, FormatVideoEncoder formatVideoEncoder,
                                       VirtualDisplay virtualDisplay) {
        if (prepareVideoEncoder(width, height, fps, bitRate, rotation, hardwareRotation, formatVideoEncoder)) {
            this.virtualDisplay = virtualDisplay;
            return true;
        }
        return false;
    }
}
