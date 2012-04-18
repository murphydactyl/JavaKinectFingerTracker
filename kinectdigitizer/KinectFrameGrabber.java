package kinectdigitizer;

import org.openkinect.freenect.*;
import java.nio.ByteBuffer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.Arrays;

public class KinectFrameGrabber {
		
	public int VIDEO_HEIGHT = 480;
	public int VIDEO_WIDTH = 640;
	public int DEPTH_HEIGHT = 480;
	public int DEPTH_WIDTH = 640;
	public int VIDEO_FRAME_SIZE = VIDEO_WIDTH * VIDEO_HEIGHT;
	public int DEPTH_FRAME_SIZE = DEPTH_WIDTH * DEPTH_HEIGHT;

	public static Context ctx;
    public Device dev;
	
	int[] pixVideo_back;
	int[] pixDepth_back;
	public int[] QuarterScaleOffsets;
	
	public double fpsVideo;
	public double fpsDepth;
	
	private long depthStartTime;
	private long videoStartTime;
	
	public long video_frame_counter = 0;
	public long depth_frame_counter = 0;
	
	public VideoFormat videoFormat;
	public DepthFormat depthFormat;
	
	public int log_level = 0;


	static {
		ctx = Freenect.createContext();		
	}
	

	public KinectFrameGrabber() {
		this(0);
	}
	
	
	public KinectFrameGrabber(int deviceNum) {
		
		videoFormat = VideoFormat.RGB;
		depthFormat = DepthFormat.D11BIT;
		
		initDevice(deviceNum);
		
		pixVideo_back = new int[VIDEO_FRAME_SIZE];
		pixDepth_back = new int[DEPTH_FRAME_SIZE];
		
		QuarterScaleOffsets = new int[DEPTH_FRAME_SIZE];
		for (int i = 0; i < DEPTH_FRAME_SIZE; i++) {
			int x = (i % DEPTH_WIDTH) / 2;
			int y = (i / DEPTH_WIDTH) / 2;
			QuarterScaleOffsets[i] = y * (DEPTH_WIDTH / 2) + x;			
		}
		
	}		
	
	public void initDevice() {
		initDevice(0);
	}
	
	Object lock = new Object();
	
	public void initDevice(int deviceNum) {
		
    	// DECLARATIONS
		dev = null;
		
    	// INITIALIZE DEVICE
		
	    if (ctx.numDevices() > 0) {
	    	dev = ctx.openDevice(deviceNum);
	    } else {
	    	System.err.println("No kinects detected.  Exiting, dude.");
	    	System.exit(0);
	    }

	}
	
	//	start both depth and video streams with current settings
	public void start() {
		startDepth();
		startVideo();		
	}
	
	//	start depth stream with current settings
	public void startDepth() {
				
		try {
			setDepthFormat(depthFormat);
			initDepth();
		} catch (InterruptedException e) {
			System.out.println("Caught interrupt in startDepth.");
		}
	}
	
		
	//	start video stream with current settings
	public void startVideo() {

		try {
			setVideoFormat(videoFormat);
			initVideo();
		} catch (InterruptedException e) {
			System.out.println("Caught interrupt exception in startVideo.");
		}
	}
		

	public void shutdown() {
	    // SHUT DOWN
		System.out.println("Shutting down...");
		if (ctx != null) {
			if (dev != null) {
				dev.close();
			}
			ctx.shutdown();
		}
		ctx = null;
		dev = null;
	}
	
	
	public void initVideo() throws InterruptedException {
		
        videoStartTime = System.nanoTime();
        dev.startVideo(new VideoHandler() {
        	int frameCount = 0;
			
        	public void onFrameReceived(VideoFormat format, ByteBuffer frame, int timestamp) {
				synchronized (lock) {
					video_frame_counter++;
					int num_samples;
					int ix = 0;
					if (pixVideo_back != null) {
						if (format == VideoFormat.IR_8BIT) {
							num_samples = VIDEO_FRAME_SIZE;
							for( int i = 0; i < num_samples;) {
								
								//int lo = frame.get(i++);
								//int a = 0xFF000000 | lo << 16 | lo << 8 | lo;
								int sample = frame.get(i++);
								pixVideo_back[ix++] = sample;
							}
						} else if (format == VideoFormat.IR_10BIT) {
							num_samples = 2 * VIDEO_FRAME_SIZE;			
							for( int i = 0; i < num_samples;) {
								int lo = frame.get(i++) & 255;
								int hi = frame.get(i++) & 255;
								//pixVideo[ix++] = hi << 8 | lo;
								int sample = (hi << 8 | lo) >> 2;
								//pixVideo[ix++] = 0xFF000000 | a << 16 | a << 8 | a;
								pixVideo_back[ix++] = sample;
							}
						} else if (format == VideoFormat.RGB) {
							num_samples = 3 * VIDEO_FRAME_SIZE;							
							for (int i = 0; i < num_samples;) {
								int sample = 0xFF000000 | (frame.get(i++) & 255) << 16 | (frame.get(i++) & 255) << 8 | (frame.get(i++) & 255);					
								pixVideo_back[ix++] = sample;
							}
						}
						
					}
				}
				frameCount++;
				if (frameCount == 30) {
					fpsVideo = ((double) System.nanoTime() - videoStartTime) / 1000000000;
					if (log_level > 0) {
						//System.out.format("Got %d video frames in %4.2fs%n", frameCount, fpsVideo);
					}
					frameCount = 0;
					videoStartTime = System.nanoTime();
				}
			}
		});
    }
	
	public void initDepth() throws InterruptedException {
		
        depthStartTime = System.nanoTime();
        dev.startDepth(new DepthHandler() {
        	int frameCount = 0;
			
        	public void onFrameReceived(DepthFormat format, ByteBuffer frame, int timestamp) {
				
				synchronized (lock) {
					depth_frame_counter++;
					
					int ix = 0;
					if (pixDepth_back != null) {
						for( int i = 0; i < format.getFrameSize();) {
							int lo = frame.get(i++) & 255;
							int hi = frame.get(i++) & 255;
							int sample = hi << 8 | lo;
							pixDepth_back[ix++] = sample;
						}
					}
				}
				frameCount++;
				if (frameCount == 30) {
					if (log_level > 0) {
						//System.out.format("Got %d depth frames in %4.2fs%n", frameCount, fpsDepth);
					}
					fpsDepth = ((double) System.nanoTime() - depthStartTime) / 1000000000;
					frameCount = 0;
					depthStartTime = System.nanoTime();
				}
            }
		});
    }
	
	public void adjustTiltAngle(double amount) {
		double currentAngle = getTiltAngle();
		double targetAngle = currentAngle + amount;
		System.out.println("New target angle: " + targetAngle + " degrees.  Adjusting...");
		setTiltAngle(targetAngle);
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			System.out.println("I was interrupted.");
		}
		double finalAngle = getTiltAngle();
		
		System.out.println("...Final angle: " + finalAngle + " degrees");	
	}
	
	public void setTiltAngle(double newAngle) {
		dev.refreshTiltState();
		dev.setTiltAngle(Math.min(28, Math.max(-28, newAngle)));
	}
	
	public double getTiltAngle() {
		dev.refreshTiltState();
		return dev.getTiltAngle();
	}
		
		
	/*
	SUPPORTED FORMATS
	VideoFormat.RGB
	VideoFormat.IR_8BIT
	VideoFormat.IR_10BIT
	
	UNSUPPORTED FORMATS (but probably easily added)
	VideoFormat.BAYER
	VideoFormat.IR_10BIT_PACKED
	VideoFormat.YUV_RGB
	VideoFormat.YUV_RAW
	*/
		
	public void setVideoFormat(VideoFormat fmt) {
		videoFormat = fmt;
		dev.setVideoFormat(fmt);
	}
		
		
	/*
	SUPPORTED FORMATS
	D11BIT

	UNSUPPORTED FORMATS
	D10BIT
	D11BIT_PACKED
	D10BIT_PACKED
	*/

	public void setDepthFormat(DepthFormat fmt) {
		depthFormat = fmt;
		dev.setDepthFormat(fmt);
	}

	public void getDepthFrame(int[] dst) {
		System.arraycopy(pixDepth_back, 0, dst, 0, DEPTH_FRAME_SIZE);
	}

	public void getVideoFrame(int[] dst) {
		System.arraycopy(pixVideo_back, 0, dst, 0, VIDEO_FRAME_SIZE);
	}
	
	public void getDepthFrameQuarterScale(int[] dst) {
		
		int avg;

	}

	public void getVideoFrameQuarterScale(int[] dst) {
		
		int avg;
				
		synchronized (lock) {		
			
			//out("Starting to scale down image");
			for (int i = 0; i < dst.length; i++) {
				int x = i % (VIDEO_WIDTH / 2);
				int y = i / (VIDEO_WIDTH / 2);
				dst[i] = pixVideo_back[2 * y * VIDEO_WIDTH + 2 * x];
				dst[i] += pixVideo_back[2 * y * VIDEO_WIDTH + 2 * x + 1];
				dst[i] += pixVideo_back[2 * y * VIDEO_WIDTH + 2 * x + VIDEO_WIDTH];
				dst[i] += pixVideo_back[2 * y * VIDEO_WIDTH + 2 * x + VIDEO_WIDTH + 1];
				dst[i] = dst[i] / 4;
			}
			//out("...Finished scaling down image");			
		}
		
			
	}
		
	
	public double getFPSVideo() {
		return fpsVideo;		
	}
	
	public double getFPSDepth() {
		return fpsDepth;
	}
	
	public void out(String msg) {
		System.out.println(msg);
	}
	
}
