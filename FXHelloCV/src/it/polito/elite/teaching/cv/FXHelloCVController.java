package it.polito.elite.teaching.cv;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import org.opencv.core.*;
import org.opencv.calib3d.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import it.polito.elite.teaching.cv.utils.Utils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * The controller for our application, where the application logic is
 * implemented. It handles the button for starting/stopping the camera and the
 * acquired video stream.
 *
 * @author <a href="mailto:luigi.derussis@polito.it">Luigi De Russis</a>
 * @author <a href="http://max-z.de">Maximilian Zuleger</a> (minor fixes)
 * @version 2.0 (2016-09-17)
 * @since 1.0 (2013-10-20)
 *
 */
public class FXHelloCVController
{
	// the FXML button
	@FXML
	private Button button;
	// the FXML image view
	@FXML
	private ImageView currentFrame;
	
	// a timer for acquiring the video stream
	private ScheduledExecutorService timer;
	// the OpenCV object that realizes the video capture
	private VideoCapture capture = new VideoCapture();
	// a flag to change the button behavior
	private boolean cameraActive = false;
	// the id of the camera to be used
	private static int cameraId = 0;
	private MultiFormatReader mf;

	/**
	 * The action triggered by pushing the button on the GUI
	 *
	 * @param event
	 *            the push button event
	 */
	@FXML
	protected void startCamera(ActionEvent event) {
		mf = new MultiFormatReader();
		Map <DecodeHintType, List> hm = new HashMap<>();
		ArrayList<BarcodeFormat> allowedFormats = new ArrayList<BarcodeFormat>();
		allowedFormats.add(BarcodeFormat.QR_CODE);
		hm.put(DecodeHintType.POSSIBLE_FORMATS, allowedFormats);
		mf.setHints(hm);

		if (!this.cameraActive) {
			// start the video capture
			this.capture.open(cameraId);
			
			// is the video stream available?
			if (this.capture.isOpened()) {
				this.cameraActive = true;
				
				// grab a frame every 33 ms (30 frames/sec)
				Runnable frameGrabber = new Runnable() {
					
					@Override
					public void run() {
						// effectively grab and process a single frame
						Mat frame = grabFrame();
						// convert and show the frame
						Image imageToShow = Utils.mat2Image(frame);
						updateImageView(currentFrame, imageToShow);
					}
				};
				
				this.timer = Executors.newSingleThreadScheduledExecutor();
				this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);
				
				// update the button content
				this.button.setText("Stop Camera");
			} else {
				// log the error
				System.err.println("Impossible to open the camera connection...");
			}
		} else {
			// the camera is not active at this point
			this.cameraActive = false;
			// update again the button content
			this.button.setText("Start Camera");
			
			// stop the timer
			this.stopAcquisition();
		}
	}
	
	/**
	 * Get a frame from the opened video stream (if any)
	 *
	 * @return the {@link Mat} to show
	 */
	private Mat grabFrame() {
		BufferedImage img;
		Result result = null;

		// init everything
		Mat frame = new Mat();
		Mat gray = new Mat();
		Mat circles = new Mat();
		
		// check if the capture is open
		if (this.capture.isOpened()) {
			try {
				// read the current frame
				this.capture.read(frame);
				
				// if the frame is not empty, process it
				if (!frame.empty()) {
					// Ting til qr-genkendelse
					img = fromMatrix(frame);
					LuminanceSource source = new BufferedImageLuminanceSource(img);
					BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

					// Lav billede grayscale
					Imgproc.cvtColor(frame, gray, Imgproc.COLOR_RGB2GRAY);
					// Blurbillede for at undgå falskt positive cirkler
					Imgproc.GaussianBlur(gray, gray, new Size(9,9), 1, 1);

					// Find cirkler
					int minRadius = 130;
					int maxRadius = 90;
					Imgproc.HoughCircles(gray, circles, Imgproc.CV_HOUGH_GRADIENT, 1, minRadius, 120, 10, minRadius, maxRadius);

					// Tegn cirkler (og udskriv)
					for( int i = 0; i < circles.rows(); i++ ) {
						double[] center = circles.get(i,0);
						int radius = (int) Math.round(center[2]);
						Point centerpoint = new Point(center);

						// udskriv log
						System.out.println("Circle");
						System.out.println("  center      : ("+center[0]+"; "+center[1]+")");
						System.out.println("  radius      : "+radius);

						// centrum
						Imgproc.circle( frame, centerpoint, 3, new Scalar(0,255,0), -1, 8, 0 );
						// outline
						Imgproc.circle( frame, centerpoint, radius, new Scalar(0,0,255), 3, 8, 0 );
					}

					// QR
					try {
						result = mf.decodeWithState(bitmap);
					} catch (NotFoundException e) {
						// fall thru, it means there is no QR code in image
					}
					if (result!=null){
						// Hent punkter fra resultatet
						ResultPoint[] resultPoints = result.getResultPoints();
						double[] blCoords = {resultPoints[0].getX(), resultPoints[0].getY()};
						double[] tlCoords = {resultPoints[1].getX(), resultPoints[1].getY()};
						double[] trCoords = {resultPoints[2].getX(), resultPoints[2].getY()};
						Point tr = new Point(trCoords);
						Point tl = new Point(tlCoords);
						Point bl = new Point(blCoords);

						// Udskriv log
						System.out.println("QR-code \""+result.getText()+"\"");
						System.out.println("  Top-right   : "+tr);
						System.out.println("  Top-left    : "+tl);
						System.out.println("  bottom-left : "+bl);

						// Tegn linjer mellem punkterne
						Imgproc.line(frame, tr, tl, new Scalar(255,0, 0), 8);
						Imgproc.line(frame, tl, bl, new Scalar(0,255, 0), 8);
						Imgproc.line(frame, bl, tr, new Scalar(0,0, 255), 8);

						// Tegn circler på punkter
						Imgproc.circle(frame, tr, 4, new Scalar(0, 0, 255));
						Imgproc.circle(frame, tl, 4, new Scalar(0,255,0));
						Imgproc.circle(frame, bl, 4, new Scalar(255,0,0));
					}

				}
				
			} catch (Exception e) {
				// log the error
				System.err.println("Exception during the image elaboration: " + e);
			}
		}
		
		return frame;
	}
	
	/**
	 * Stop the acquisition from the camera and release all the resources
	 */
	private void stopAcquisition() {
		if (this.timer!=null && !this.timer.isShutdown()) {
			try {
				// stop the timer
				this.timer.shutdown();
				this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				// log any exception
				System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
			}
		}
		if (this.capture.isOpened()) {
			// release the camera
			this.capture.release();
		}
	}
	
	/**
	 * Update the {@link ImageView} in the JavaFX main thread
	 * 
	 * @param view
	 *            the {@link ImageView} to update
	 * @param image
	 *            the {@link Image} to show
	 */
	private void updateImageView(ImageView view, Image image)
	{
		Utils.onFXThread(view.imageProperty(), image);
	}
	
	/**
	 * On application close, stop the acquisition from the camera
	 */
	protected void setClosed()
	{
		this.stopAcquisition();
	}


	// Tyvstjålet fra https://github.com/Trivivium/CDIO-4/blob/master/src/cdio/utilities/ImageUtils.java
	public static BufferedImage fromMatrix(Mat matrix) {
		BufferedImage image;


		if(matrix.channels() > 1) {
			image = new BufferedImage(matrix.width(), matrix.height(), BufferedImage.TYPE_3BYTE_BGR);
		}
		else {
			image = new BufferedImage(matrix.width(), matrix.height(), BufferedImage.TYPE_BYTE_GRAY);
		}

		final byte[] source = new byte[matrix.width() * matrix.height() * matrix.channels()];
		final byte[] dest = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

		matrix.get(0, 0, source);
		System.arraycopy(source, 0, dest, 0, source.length);

		return image;
	}
	// Tyvstjålet fra https://github.com/Trivivium/CDIO-4/blob/master/src/cdio/utilities/ImageUtils.java
	public static Mat toMatrix(BufferedImage image) {
		Mat    mat  = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC3);
		byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

		mat.put(0, 0, data);

		return mat;
	}
}
