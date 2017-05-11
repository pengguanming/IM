package nfgnfg.example.com.im;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    @BindView(R.id.file_mode)
    Button mFileMode;
    @BindView(R.id.stream_mode)
    Button mStreamMode;
    @BindView(R.id.visualize_mode)
    Button mVisualizeMode;
    @BindView(R.id.effect_mode)
    Button mEffectMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
    }

    @OnClick({R.id.file_mode, R.id.stream_mode, R.id.visualize_mode,R.id.effect_mode})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.file_mode:
                startActivity(new Intent(this, FileActivity.class));
                break;
            case R.id.stream_mode:
                startActivity(new Intent(this, StreamActivity.class));
                break;
            case R.id.visualize_mode:
                startActivity(new Intent(this, VisualizerActivity.class));
                break;
            case R.id.effect_mode:
                startActivity(new Intent(this, EffectActivity.class));
                break;
        }
    }
}
