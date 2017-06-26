package com.pedro.rtmpstreamer.defaultexample;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.pedro.builder.RtspBuilder;
import com.pedro.rtmpstreamer.R;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;

/**
 * This class is only for a simple example of library use with default stream values
 * video = 1280x720 resolution, 30fps, 1500 * 1024 bitrate, 0 rotation
 * audio = stereo, 128 * 1024 bitrate, 44100 sampleRate
 */
public class ExampleRtspActivity extends AppCompatActivity
    implements ConnectCheckerRtsp, View.OnClickListener {

  private RtspBuilder rtspBuilder;
  private Button button;
  private EditText etUrl;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_example_rtsp);
    SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
    button = (Button) findViewById(R.id.b_start_stop);
    button.setOnClickListener(this);
    etUrl = (EditText) findViewById(R.id.et_rtsp_url);
    rtspBuilder = new RtspBuilder(surfaceView, Protocol.UDP, this);
  }

  @Override
  public void onConnectionSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(ExampleRtspActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onConnectionFailedRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(ExampleRtspActivity.this, "Connection failed", Toast.LENGTH_SHORT).show();
        rtspBuilder.stopStream();
        button.setText(getResources().getString(R.string.start_button));
      }
    });
  }

  @Override
  public void onDisconnectRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(ExampleRtspActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthErrorRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(ExampleRtspActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(ExampleRtspActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onClick(View view) {
    if (!rtspBuilder.isStreaming()) {
      if (rtspBuilder.prepareAudio() && rtspBuilder.prepareVideo()) {
        button.setText(getResources().getString(R.string.stop_button));
        rtspBuilder.startStream(etUrl.getText().toString());
      } else {
        Toast.makeText(this, "Error preparing stream, This device cant do it", Toast.LENGTH_SHORT)
            .show();
      }
    } else {
      button.setText(getResources().getString(R.string.start_button));
      rtspBuilder.stopStream();
    }
  }
}