package com.pedro.rtmpstreamer.defaultexample;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.insthync.simplescreenrtmp.builder.ScreenRtmpBuilder;
import com.pedro.rtmpstreamer.R;

import net.ossrs.rtmp.ConnectCheckerRtmp;

public class ExampleScreenRtmpActivity extends AppCompatActivity
    implements ConnectCheckerRtmp, View.OnClickListener  {
    private static final int REQUEST_CODE = 1;
    private static final int REQUEST_STREAM = 2;
    private static String[] PERMISSIONS_STREAM = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAPTURE_VIDEO_OUTPUT,
            Manifest.permission.CAPTURE_AUDIO_OUTPUT,
    };

    private ScreenRtmpBuilder rtmpBuilder;
    private Button button;
    private EditText etUrl;
    MediaProjectionManager mediaProjectionManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_screen_rtmp);

        button = (Button) findViewById(R.id.b_start_stop);
        button.setOnClickListener(this);
        etUrl = (EditText) findViewById(R.id.et_rtmp_url);
        etUrl.setText("rtmp://188.166.191.129/live/insthync");
        rtmpBuilder = new ScreenRtmpBuilder(this);

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        verifyPermissions();
    }

    public void verifyPermissions() {
        for (String permission : PERMISSIONS_STREAM) {
            int permissionResult = ActivityCompat.checkSelfPermission(ExampleScreenRtmpActivity.this, permission);
            if (permissionResult != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        ExampleScreenRtmpActivity.this,
                        PERMISSIONS_STREAM,
                        REQUEST_STREAM
                );
                return;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STREAM) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {

            if (rtmpBuilder.prepareAudio() && rtmpBuilder.prepareVideo()) {
                if (Build.VERSION.SDK_INT >= 21) {
                    rtmpBuilder.startStream(mediaProjectionManager.getMediaProjection(resultCode, data), etUrl.getText().toString());
                }
            } else {
                Toast.makeText(this, "Error preparing stream, This device cant do it", Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }


    @Override
    public void onConnectionSuccessRtmp() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ExampleScreenRtmpActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onConnectionFailedRtmp() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ExampleScreenRtmpActivity.this, "Connection failed", Toast.LENGTH_SHORT).show();
                rtmpBuilder.stopStream();
                button.setText(getResources().getString(R.string.start_button));
            }
        });
    }

    @Override
    public void onDisconnectRtmp() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ExampleScreenRtmpActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onAuthErrorRtmp() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ExampleScreenRtmpActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onAuthSuccessRtmp() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ExampleScreenRtmpActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onClick(View view) {
        if (!rtmpBuilder.isStreaming()) {
            button.setText(getResources().getString(R.string.stop_button));
            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, REQUEST_CODE);
        } else {
            button.setText(getResources().getString(R.string.start_button));
            rtmpBuilder.stopStream();
        }
    }
}
