package nfgnfg.example.com.im;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

public class EffectActivity extends AppCompatActivity {
    @BindView(R.id.stream_record)
    Button mStreamRecord;
    @BindView(R.id.stream_content)
    TextView mStreamContent;
    @BindView(R.id.stream_play)
    Button mStreamPlay;
    @BindView(R.id.stream_accelerate)
    Button mStreamAccelerate;
    @BindView(R.id.stream_decelerate)
    Button mStreamDecelerate;
    //主线程和后台播放线程数据同步
    private volatile boolean mIsPlaying;
    //支持播放的频率
    private static final int[]SUPPORT_SAMPLE_RATE={11025,22050,44100};
    private ExecutorService mExecutorService;
    private Handler mMainThreadHandler;
    //录音状态，volatile，保证多线程同步 ，避免出问题
    private volatile boolean mIsRecording;
    private File mAudioFile;
    private long mStartRecordTime, mStopRecordTime;
    private byte[] mBuffer;
    //buffer不能太大，避免OOM
    private static final int BUFFER_SIZE = 2048;
    private FileOutputStream mFileOutputStream;
    private AudioRecord mAudioRecord;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_effect);
        ButterKnife.bind(this);
        //录音jni函数不具备线程安全性，所以要用单线程。
        mExecutorService = Executors.newSingleThreadExecutor();
        mMainThreadHandler = new Handler(Looper.getMainLooper());
        mBuffer = new byte[BUFFER_SIZE];
    }

    //activity销毁时，停止后台任务，避免内存泄漏
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutorService.shutdownNow();
    }

    @OnClick(R.id.stream_record)
    public void onRecord() {
        //根据当前状态，改变UI，执行开始/停止录音的逻辑
        if (mIsRecording) {
            //改变录音状态
            mIsRecording = false;
            mStreamRecord.setText("开始录音");
        } else {
            //改变录音状态
            mIsRecording = true;
            mStreamRecord.setText("停止录音");
            //提交后台任务，执行录音逻辑
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    //执行录音逻辑，失败提示用户
                    if (!startRecord()) {
                        recordFail();
                    }
                }
            });
        }
    }

    //录音失败提示用户
    private void recordFail() {
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(EffectActivity.this, "录音失败", Toast.LENGTH_SHORT).show();
                //重置录音状态，以及UI状态
                mIsRecording = false;
                mStreamRecord.setText("开始录音");
            }
        });
    }

    //启动录音逻辑
    private boolean startRecord() {
        try {
            //创建录音文件
            Date date = new Date();
            String time = new SimpleDateFormat("yyyyMMddhhmmss").format(date);
            mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/imoocDemo/"
                    + time + ".pcm");
            mAudioFile.getParentFile().mkdirs();
            mAudioFile.createNewFile();
            // 创建文件输出流
            mFileOutputStream = new FileOutputStream(mAudioFile);
            //设置AudioRecord
            int audioSource = MediaRecorder.AudioSource.MIC;//从麦克风采集
            //22050采样频率用44100播放频率实现2倍加速效果，
            //但因为声音的频率决定音调，播放会影响音调
            int sampleRate = 22050;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;//单声道输入
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;//PCM 16是所有安卓系统都支持
            int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);//计算AudioRecord 内部buffer的最小的大小
            mAudioRecord = new AudioRecord(audioSource, sampleRate, channelConfig, audioFormat,
                    Math.max(minBufferSize, BUFFER_SIZE));//buffer不能小于最低要求，也不能小于我们每次读取的大小
            //开始录音
            mAudioRecord.startRecording();
            //记录开始录音时间，用于统计时长
            mStartRecordTime = System.currentTimeMillis();
            //循环读取数据，写到输出流中
            while (mIsRecording) {
                //只要还在录音状态，就一直读取数据
                int read = mAudioRecord.read(mBuffer, 0, BUFFER_SIZE);
                if (read > 0) {
                    //读取成功就写到文件中
                    mFileOutputStream.write(mBuffer, 0, read);
                } else {
                    //读取失败，返回false提示用户
                    return false;
                }
            }
            //退出循环,停止录音，释放资源
            return stopRecord();
        } catch (IOException | RuntimeException e) {
            //捕获异常，避免闪退，返回false提示用户
            e.printStackTrace();
            return false;
        } finally {
            //释放AudioRecord
            if (mAudioRecord != null) {
                mAudioRecord.release();
                mAudioRecord = null;
            }
        }
    }

    //结束录音逻辑
    private boolean stopRecord() {
        try {
            //停止录音，关闭文件输出流
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
            mFileOutputStream.close();
            mStopRecordTime = System.currentTimeMillis();
            //只接受超过3秒的录音，在UI上显示出来
            final int second = (int) (mStopRecordTime - mStartRecordTime) / 1000;
            if (second > 3) {
                //在UI显示出来
                mMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mStreamContent.setText(mStreamContent.getText() + "\n录音成功" + second + "秒");
                    }
                });
            }
        } catch (IOException e) {
            //捕获异常，避免闪退，返回false提示用户
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @OnClick(R.id.stream_play)
    public void onPlayNormal() {
        //检查播放状态，防止重复播放
        if (mAudioFile != null && !mIsPlaying) {
            //设置当前为播放状态
            mIsPlaying = true;
            //在后台线程提交播放任务，防止阻塞主线程
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    doPlay(SUPPORT_SAMPLE_RATE[1]);
                }
            });
        }
    }

    //后台播放逻辑
    private void doPlay(int supportSampleRate) {
        //配置播放器
        //音乐类型 扬声器播放
        int streamType = AudioManager.STREAM_MUSIC;
        //录音时采用的采样频率 所以播放时候使用同样的采样频率
        int sampleRate = supportSampleRate;
        //MONO表示单声道 录音输入单声道 播放用输出单声道
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        //录音时使用16bit 所以播放时使用同样的格式 数据位宽
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        //流模式 Java和native层数据传输模式 MODE_STATIC在播放之前一次性加载完
        int mode = AudioTrack.MODE_STREAM;//源源不断地写
        //计算最小buffer大小
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        //构造AudioTrack
        AudioTrack mAudioTrack = new AudioTrack(streamType, sampleRate, channelConfig, audioFormat, Math.max(minBufferSize, BUFFER_SIZE), mode);
        FileInputStream inputStream = null;
        //从文件流读取数据
        try {
            inputStream = new FileInputStream(mAudioFile);
            //循环读数据 写到播放器去播放
            mAudioTrack.play();
            int read;
            while ((read = inputStream.read(mBuffer)) > 0) {
                int ret = mAudioTrack.write(mBuffer, 0, read);
                //检查write返回值，错误处理
                switch (ret) {
                    case AudioTrack.ERROR_INVALID_OPERATION:
                    case AudioTrack.ERROR_BAD_VALUE:
                    case AudioTrack.ERROR_DEAD_OBJECT:
                        playFail();
                        return;
                    default:
                        break;
                }
            }
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            //处理错误，防止闪退
            playFail();
        } finally {
            mIsPlaying = false;
            //关闭文件输入流
            if (inputStream != null) {
                closeQuitly(inputStream);
            }
            //释放播放器
            resetQuitly(mAudioTrack);
        }
    }

    private void resetQuitly(AudioTrack mAudioTrack) {
        mAudioTrack.stop();
        mAudioTrack.release();
    }

    private void closeQuitly(FileInputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //提示播放失败
    private void playFail() {
        mAudioFile = null;
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(EffectActivity.this, "播放失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @OnClick({R.id.stream_accelerate, R.id.stream_decelerate})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.stream_accelerate:
                //检查播放状态，防止重复播放
                if (mAudioFile != null && !mIsPlaying) {
                    //设置当前为播放状态
                    mIsPlaying = true;
                    //在后台线程提交播放任务，防止阻塞主线程
                    mExecutorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            doPlay(SUPPORT_SAMPLE_RATE[0]);
                        }
                    });
                }
                break;
            case R.id.stream_decelerate:
                //检查播放状态，防止重复播放
                if (mAudioFile != null && !mIsPlaying) {
                    //设置当前为播放状态
                    mIsPlaying = true;
                    //在后台线程提交播放任务，防止阻塞主线程
                    mExecutorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            doPlay(SUPPORT_SAMPLE_RATE[2]);
                        }
                    });
                }
                break;
        }
    }
}
