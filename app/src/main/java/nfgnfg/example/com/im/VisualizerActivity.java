package nfgnfg.example.com.im;

import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.BindViews;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by ngfngf on 2017/5/8.
 */

public class VisualizerActivity extends AppCompatActivity {
    @BindView(R.id.mTVLog)
    TextView mMTVLog;
    @BindView(R.id.mTVPressToSay)
    TextView mMTVPressToSay;
    @BindView(R.id.file_play)
    Button mFilePlay;
    @BindView(R.id.mFlIndicator)
    FrameLayout mMFlIndicator;
    @BindViews({R.id.volume7, R.id.volume6, R.id.volume5, R.id.volume4, R.id.volume3, R.id.volume2, R.id.volume1})
    List<ImageView> mIVVoiceIndicator;

    private ScheduledExecutorService mExecutorService;
    private MediaRecorder mMediaRecorder;
    private File mAudioFile;
    private long mStartRecordTime, mStopRecordTime;
    private Handler mMainThreadHandler;
    //主线程和后台播放线程数据同步
    private volatile boolean mIsPlaying;
    private MediaPlayer mMediaPlayer;
    private Random mRandom;
    //MediaRecord.getMaxAmplitude返回最大值是32767
    private static final int MAX_AMPLITUDE = 32767;
    private static final int MAX_LEVEL = 8;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_visualizer);
        ButterKnife.bind(this);
        //录音jni函数不具备线程安全性，所以要用单线程。
        mExecutorService = Executors.newSingleThreadScheduledExecutor();
        mMainThreadHandler = new Handler(Looper.getMainLooper());
        mRandom = new Random(System.currentTimeMillis());
        mMTVPressToSay.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startRecord();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        stopRecord();
                        break;
                    default:
                        break;
                }
                //处理了touch事件，返回TRUE
                return true;
            }
        });
    }

    //activity销毁时，停止后台任务，避免内存泄漏
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutorService.shutdownNow();
        releaseRecorder();
        stopPlay();
    }

    //停止录音
    private void stopRecord() {
        mMTVPressToSay.setText("按住说话");
        mMTVPressToSay.setBackgroundColor(Color.GRAY);
        //隐藏音量提示UI
        mMFlIndicator.setVisibility(View.GONE);
        //提交后台任务，执行录音逻辑
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                //执行停止录音逻辑，失败就要提醒用户
                if (!doStop()) {
                    recordFail();
                }
                //释放MediaRecorder
                releaseRecorder();
            }
        });
    }

    //开始录音
    private void startRecord() {
        mMTVPressToSay.setText("正在说话");
        mMTVPressToSay.setBackgroundColor(Color.WHITE);
        //显示提示View
        mMFlIndicator.setVisibility(View.VISIBLE);

        //提交后台任务，执行停止逻辑
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                //释放之前录音的 recorder
                releaseRecorder();
                //执行录音逻辑，如果失败提示用户
                if (!doStart()) {
                    recordFail();
                }
            }
        });
        //提交后台获取音量任务
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                monitorRecordAmplitude();
            }
        });
    }

    //定期获取录音的音量
    private void monitorRecordAmplitude() {
        if (mMediaRecorder == null) {
            return;
        }
        int amplitude;
        try {
            //获取音量大小
            amplitude = mMediaRecorder.getMaxAmplitude();
            Log.e("JJY", "getMaxAmplitude: " + amplitude);
        } catch (IllegalStateException e) {
            //避免闪退，异常发生后，用一个随机数代表当前音量大小
            e.printStackTrace();
            amplitude = mRandom.nextInt(MAX_AMPLITUDE);
        }
        //把音量规划到8个等级
        final int level = amplitude / (MAX_AMPLITUDE / MAX_LEVEL);
        Log.e("JJY", "level: " + level);
        //把等级显示到UI上
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                refreshAudioAmplitude(level);
            }
        });
        //如果还在录音 50ms之后，再次获取音量大小
        if (mIsPlaying==false) {
            mExecutorService.schedule(new Runnable() {
                @Override
                public void run() {
                    Log.e("jjy", "run: ");
                    monitorRecordAmplitude();
                }
            }, 50, TimeUnit.MILLISECONDS);
        }
    }

    //显示音量的等级
    private void refreshAudioAmplitude(int level) {
        //对所有的ImageView进行遍历，如果它的位置小于level，就应该显示
        //i<level,而不是i<=level，否则0级就会显示一个
        Log.e("JJY", "level2: " + level);
        Log.e("jjy", "mIsPlaying: "+mIsPlaying );
        for (int i = 0; i < mIVVoiceIndicator.size(); i++) {
            mIVVoiceIndicator.get(i).setVisibility(i < level ? View.VISIBLE : View.INVISIBLE);
        }
    }

    //录音失败
    private void recordFail() {
        mMediaRecorder = null;
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(VisualizerActivity.this, "录音失败", Toast.LENGTH_SHORT).show();
            }
        });

    }

    //释放MediaRecorder
    private void releaseRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    //启动录音逻辑
    private boolean doStart() {
        try {
            //创建MediaRecorder
            mMediaRecorder = new MediaRecorder();
            Date date = new Date();
            String time = new SimpleDateFormat("yyyyMMddhhmmss").format(date);
            Log.d("jjy", "doStart: " + time);
            //创建录音文件
            mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/imoocDemo/"
                    + time + ".m4a");
            mAudioFile.getParentFile().mkdirs();
            mAudioFile.createNewFile();
            //配置MediaRecorder
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);//声音采集来自于麦克风
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);//保存格式为Mp4
            mMediaRecorder.setAudioSamplingRate(44100);//所有安卓系统都支持的采样频率
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);//通用的AAC编码格式
            mMediaRecorder.setAudioEncodingBitRate(96000);//音质比较好的频率
            mMediaRecorder.setOutputFile(mAudioFile.getAbsolutePath());//设置文件保存路径
            //开始录音
            mMediaRecorder.prepare();
            mMediaRecorder.start();
            //记录开始录音的时间，用于统计时长
            mStartRecordTime = System.currentTimeMillis();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            //捕获异常，避免闪退，返回false，提醒用户失败
            return false;
        }
        //录音成功
        return true;
    }

    //停止录音的逻辑
    private boolean doStop() {
        try {
            //停止录音
            mMediaRecorder.stop();
            //记录停止时间，统计时长
            mStopRecordTime = System.currentTimeMillis();
            //只接受超过3秒的录音，在UI上显示出来
            final int second = (int) (mStopRecordTime - mStartRecordTime) / 1000;
            Log.d("jjy", "second: " + second + ";startTime" + mStartRecordTime + ";stopTime" + mStopRecordTime);
            if (second > 3) {
                //在UI显示出来
                mMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mMTVLog.setText(mMTVLog.getText() + "\n录音成功" + second + "秒");
                    }
                });
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        //停止成功
        return true;
    }

    @OnClick(R.id.file_play)
    public void onClick() {
        //检查当前状态，防止重复播放
        if (mAudioFile != null && !mIsPlaying) {
            //设置播放状态
            mIsPlaying = true;
            //提交后台任务，开始播放
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    doPlay(mAudioFile);
                }
            });
        }
    }

    //实际播放逻辑
    private void doPlay(File audioFile) {
        //配置播放器MediaPlayer
        mMediaPlayer = new MediaPlayer();
        try {
            //设置声音文件
            mMediaPlayer.setDataSource(audioFile.getAbsolutePath());
            //设置监听回调
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    //释放播放器
                    stopPlay();
                }
            });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    //提示用户
                    playFail();
                    //释放播放器
                    stopPlay();
                    //错误已经处理，返回true
                    return true;
                }
            });
            //配置音量，是否循环
            mMediaPlayer.setVolume(1.0f, 1.0f);
            mMediaPlayer.setLooping(false);
            //准备，开始
            mMediaPlayer.prepare();
            mMediaPlayer.start();
        } catch (IOException | RuntimeException e) {
            //异常处理，防止闪退
            e.printStackTrace();
            //提示用户播放失败
            playFail();
        }
    }

    //提示播放失败
    private void playFail() {
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(VisualizerActivity.this, "播放失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    //停止播放
    private void stopPlay() {
        //重置播放状态
        mIsPlaying = false;
        //释放播放器
        if (mMediaPlayer != null) {
            //重置监听器，防止内存泄漏
            mMediaPlayer.setOnCompletionListener(null);
            mMediaPlayer.setOnErrorListener(null);
            mMediaPlayer.stop();
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

}

