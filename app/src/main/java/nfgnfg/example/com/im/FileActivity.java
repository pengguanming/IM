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
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by ngfngf on 2017/5/8.
 */

public class FileActivity extends AppCompatActivity {
    @BindView(R.id.mTVLog)
    TextView mMTVLog;
    @BindView(R.id.mTVPressToSay)
    TextView mMTVPressToSay;
    @BindView(R.id.file_play)
    Button mFilePlay;
    private ExecutorService mExecutorService;
    private MediaRecorder mMediaRecorder;
    private File mAudioFile;
    private long mStartRecordTime, mStopRecordTime;
    private Handler mMainThreadHandler;
    //主线程和后台播放线程数据同步
    private volatile boolean mIsPlaying;
    private MediaPlayer mMediaPlayer;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file);
        ButterKnife.bind(this);
        //录音jni函数不具备线程安全性，所以要用单线程。
        mExecutorService = Executors.newSingleThreadExecutor();
        mMainThreadHandler = new Handler(Looper.getMainLooper());
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
    }

    //录音失败
    private void recordFail() {
        mMediaRecorder = null;
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FileActivity.this, "录音失败", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(FileActivity.this, "播放失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    //停止播放
    private void stopPlay() {
        //重置播放状态
        mIsPlaying = false;
        //释放播放器
        if (mMediaPlayer!=null){
            //重置监听器，防止内存泄漏
            mMediaPlayer.setOnCompletionListener(null);
            mMediaPlayer.setOnErrorListener(null);
            mMediaPlayer.stop();
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer=null;
        }
    }

}

