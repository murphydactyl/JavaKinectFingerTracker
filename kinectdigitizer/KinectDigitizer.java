package kinectdigitizer;

import org.openkinect.freenect.*;
import canvasframe.*;
import imageprocessor.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.swing.event.*;
import java.util.Arrays;

public class KinectDigitizer extends KinectFrameGrabber
{
	
	static double RGB_FOCAL_LENGTH = 525;	// in pixels
	static double IR_FOCAL_LENGTH = 580;	// in pixels
	
	static double RGB_OFFSET_X = -2.32;		// in cm wrt IR camera
	static double RGB_OFFSET_Y = -1.49;		// in cm wrt IR camera
	static double RGB_OFFSET_Z = -1.29;		// in cm wrt IR camera
	
	public int[] pixDepth = new int[640 * 480];
	public int[] pixDepth_ref = new int[640 * 480];
	public int[] pixTmp = new int[640 * 480];
	public int[] pixPlane = new int[640 * 480];
	public double[] xyz = new double[640 * 480 * 3];
	
	public double offsetx = 0.0;
	public double offsety = 0.0;
	public double offsetz = 0.0;
	
	public KinectDigitizer() {
		super(0);
	}
	
	public KinectDigitizer(int deviceNum) {
		super(deviceNum);				
	}
	
	/////////////////////////////////////////////////////////////////////////////////
	/* METHODS TO INFER ABSOLUTE XYZ FROM DEPTH IMAGE WITH CAMERA SENSOR AS ORIGIN */
	/////////////////////////////////////////////////////////////////////////////////	
	float getWorldZ(int depth) {
		return (7.5f * (float)IR_FOCAL_LENGTH * 8.0f) / (1091.5f - depth);
		
	}

	float getWorldX(int col, float worldZ) {
		return worldZ * (320.0f - col) / (float)IR_FOCAL_LENGTH;
	}
	
	
	float getWorldY(int row, float worldZ) {
		return worldZ * (240.0f - row) / (float)IR_FOCAL_LENGTH;		// right-handed
		
	}
	

	double getWorldX(int col, double worldZ) {
		return worldZ * (320.0 - col) / IR_FOCAL_LENGTH;
	}
	
	double getWorldY(int row, double worldZ) {
		return worldZ * (240.0 - row) / IR_FOCAL_LENGTH;				// right-handed
	}
	
	/////////////////////////////////////////////////////////////////////////////////
	/* METHODS TO INFER SCREEN COORDINATES WITH CAMERA SENSOR AS ORIGIN */
	/////////////////////////////////////////////////////////////////////////////////	
	public int getScreenX(float worldX, float worldZ) {
		return (int)(320.0f - (float)IR_FOCAL_LENGTH * worldX / worldZ);		// right-handed		
	}	
	
	public int getScreenY(float worldY, float worldZ) {
		return (int)(240.0f - (float)IR_FOCAL_LENGTH * worldY / worldZ);		// right-handed		
	}

		
	double[] xyd2xyz(int row, int col, int depth) {
		double[] xyz = new double[3];
		double z = getWorldZ(depth);
		double x = getWorldX(col, z);
		double y = getWorldY(row, z);
		xyz[0] = x;
		xyz[1] = y;
		xyz[2] = z;
		return xyz;
	}

	public void xyd2xyz(int row, int col, int depth, double[] xyz) {
		double z = getWorldZ(depth);
		double x = getWorldX(col, z);
		double y = getWorldY(row, z);
		xyz[0] = x;
		xyz[1] = y;
		xyz[2] = z;
	}

	public void xyd2xyz(int row, int col, int[] depth, double[] xyz) {
		double z = getWorldZ(depth[row * 640 + col]);
		double x = getWorldX(col, z);
		double y = getWorldY(row, z);
		xyz[0] = x;
		xyz[1] = y;
		xyz[2] = z;
	}
	

	/* return the index of rgb buffer corresponding to depth index */
	public double[] tmpXYZ = new double[3];
	public int depth2rgb(int[] depBuf, int idx) {
		xyd2xyz(idx / 640, idx % 640, depBuf[idx], tmpXYZ);
		double X = tmpXYZ[0] + RGB_OFFSET_X; // centimeters translation from ir
		double Y = tmpXYZ[1] + RGB_OFFSET_Y; // ditto
		double Z = tmpXYZ[2] + RGB_OFFSET_Z; // ditto
		int rgbCol = (int)Math.round(320 - RGB_FOCAL_LENGTH * (X / Z));
		int rgbRow = (int)Math.round(240 - RGB_FOCAL_LENGTH * (Y / Z));
		return rgbCol + 640 * rgbRow;
	}
	
	/////////////////////////////////////////////////////////////////////////////////
	/* METHODS TO INFER ABSOLUTE XYZ FROM DEPTH IMAGE WITH QUAD AS ORIGIN          */
	/////////////////////////////////////////////////////////////////////////////////
	public void depth2xyz(int row, int col, int depth, double[] outxyz) {
		double z = getWorldZ(depth);
		double x = getWorldX(col, z);
		double y = getWorldY(row, z);
	}
	
	public void depth2xyz(int row, int col, int[] depthBuf, double[] outxyz) {
		double z = getWorldZ(depthBuf[row * 640 + col]);
		double x = getWorldX(col, z);
		double y = getWorldY(row, z);
	}

	public void measureBuffer(int[] buf, double[] out) {
		double[] xyz = new double[3];
		int k = 0;
		for (int i = 0; i < buf.length; i++) {
			int x = i % 640;
			int y = i / 640;
			int d = buf[i];
			depth2xyz(x, y, d, xyz);
			out[k++] = xyz[0];
			out[k++] = xyz[1];
			out[k++] = xyz[2];
		}
	}

	///////////////////////////////////////////////////////////////////////////////
	/* METHODS FOR FILE I/O */
	///////////////////////////////////////////////////////////////////////////////
	void writeDoubleMatrix2DToFile(String filename, double[][] buf) {
		//String filename = "reference_image.dat";
		
		try {
			System.out.println("Attempting to write file named " + filename);
			FileWriter fstream = new FileWriter(filename, false);
			BufferedWriter out = new BufferedWriter(fstream);
			
			String magic = "DOUBLE " + buf.length + " " + buf[0].length + "\n";
			out.write(magic);
			
			for (int i = 0; i < buf.length; i++) {
				for (int j = 0; j < buf[i].length; j++) {
					out.write(buf[i][j] + "\n");
				}
			}
			out.close();
		} catch (IOException ioe) {
			System.out.println("Problem saving int buffer to disk.");
		}		
	}
	
	void readDoubleMatrix2DFromFile(String filename, double[][] mat) {
		
		try {
			FileReader fstream = new FileReader(filename);			
			if (fstream != null) {
				BufferedReader in = new BufferedReader(fstream);
				String s;
				int linecount = 0;
				try {
					s = in.readLine();
					String[] magic = s.split(" ");
					int rows = Integer.parseInt(magic[1]);
					int cols = Integer.parseInt(magic[2]);					
					mat = new double[rows][cols];
					out("Magic line: " + magic[0] + " " + magic[1] + " " + magic[2]);
					while((s = in.readLine()) != null) {						
						double sample = Double.parseDouble(s);
						mat[linecount / 4][linecount % 4] = sample;
						linecount++;
					}
					in.close();
					for (int i = 0; i < rows; i++) {
						out(Arrays.toString(mat[i]));
					}
				} catch (IOException e) {
				}
			}
		} catch (FileNotFoundException ioe) {
			System.out.println("File file not found.  Can't read int buffer.  Hopefully no big deal.");
		}
	}
	
	void writeIntBufferToFile(String filename, int[] buf) {
		
		//String filename = "reference_image.dat";
		
		try {
			System.out.println("Attempting to write file named " + filename);
			FileWriter fstream = new FileWriter(filename, false);
			BufferedWriter out = new BufferedWriter(fstream);
			
			for (int i = 0; i < buf.length; i++) {
				out.write(buf[i] + "\n");				
			}
			out.close();
		} catch (IOException ioe) {
			System.out.println("Problem saving int buffer to disk.");
		}
		
	}
	
	void readIntBufferFromFile(String filename, int[] buf) {
		
		//String filename = "reference_image.dat";
		
		try {
			FileReader fstream = new FileReader(filename);			
			if (fstream != null) {
				BufferedReader in = new BufferedReader(fstream);
				String s;
				int linecount = 0;
				try {
					while((s = in.readLine()) != null) {
						int sample = Integer.parseInt(s);
						buf[linecount++] = sample;
					}
					in.close();
				} catch (IOException e) {
				}
			}
		} catch (FileNotFoundException ioe) {
			System.out.println("File file not found.  Can't read int buffer.  Hopefully no big deal.");
		}
	}
	
	public void out(String msg) {
		System.out.println(msg);
	}
}

