package application;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.nio.file.WatchService;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

import javafx.application.Application;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import jssc.SerialPortList;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

public class Main extends Application {
	private SerialPort port;
	private byte[] raw = new byte[1024 * 40 * 4]; // received bytes, 160kB max.
	private int head_raw = 0; // head of received bytes
	private String pathDir = ""; // directory to save pictures
	
	private ImageView ivLatest = new ImageView(new Image("assets/default.jpg"));
	private TextField tfImage = new TextField();
	private ArrayList<File> imageList = new ArrayList<>();
	private int head_imagePathList = 0;

	public void init() {
		for (int i = 0; i < raw.length; i++)
			raw[i] = '0';
		head_raw = 0;
	}

	@Override
	public void start(Stage primaryStage) {
		try {
			VBox root = new VBox();
			root.setSpacing(5);
			Scene scene = new Scene(root, 440, 420);
			scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
			
			scene.setOnKeyPressed(e -> {
				switch (e.getCode()) {
				case LEFT:
				case KP_LEFT:
					if (head_imagePathList > 0) showPic(head_imagePathList--);
					break;
				case RIGHT:
				case KP_RIGHT:
					if (head_imagePathList < imageList.size()-1) showPic(head_imagePathList++);
				default:
					break;
				}
			});
			
			// Show Picture
			ivLatest.setPreserveRatio(true);
			ivLatest.setFitWidth(400);
			
			HBox hbImageBox = new HBox();
			hbImageBox.getChildren().addAll(ivLatest);
			hbImageBox.setSpacing(5);
			hbImageBox.setPadding(new Insets(10, 5, 10, 5));
			hbImageBox.setAlignment(Pos.CENTER);
			
			Label lbImagePath = new Label("Image:");
			lbImagePath.setAlignment(Pos.CENTER_RIGHT);
			lbImagePath.setPrefWidth(60);
			
			tfImage.setEditable(false);
			tfImage.setPrefWidth(235);
			
			Button btnImageLeft = new Button("<");
			btnImageLeft.setOnMouseClicked(e -> {
				if (head_imagePathList > 0) showPic(head_imagePathList--);
			});
			
			Button btnImageRight = new Button(">");
			btnImageRight.setOnMouseClicked(e -> {
				if (head_imagePathList < imageList.size()-1) showPic(head_imagePathList++);
			});
			
			Button btnImageHead = new Button(">>");
			btnImageHead.setOnMouseClicked(e -> {
				head_imagePathList = imageList.size()-1;
				showPic(head_imagePathList);
			});
			
			HBox hbImageInfo = new HBox();
			hbImageInfo.getChildren().addAll(lbImagePath, tfImage, btnImageLeft, btnImageRight, btnImageHead);
			hbImageInfo.setSpacing(5);
			hbImageInfo.setPadding(new Insets(0, 5, 0, 5));
			hbImageInfo.setAlignment(Pos.CENTER_LEFT);
			
			// File Info
			
			// Log Folder Config
			Label lbPath = new Label("Directory:");
			lbPath.setAlignment(Pos.CENTER_RIGHT);
			lbPath.setPrefWidth(60);

			TextField tfPath = new TextField();
			tfPath.setEditable(false);
			pathDir = Paths.get("").toAbsolutePath().toString(); // for first time
			tfPath.setText(pathDir);
			tfPath.setPrefWidth(280);

			Button btnPath = new Button("Select");
			btnPath.setOnAction(e -> {
				DirectoryChooser dc = new DirectoryChooser();
				File selectedDir = dc.showDialog(primaryStage);

				if (selectedDir == null) {
					Alert al = new Alert(AlertType.WARNING);
					al.setContentText("Didn't Selected Directory: Pictures will be saved in " + pathDir);
					al.show();
				} else {
					pathDir = selectedDir.getAbsolutePath();
					tfPath.setText(pathDir);
					System.out.println("Path: " + pathDir);
				}
			});

			HBox hbPath = new HBox();
			hbPath.getChildren().addAll(lbPath, tfPath, btnPath);
			hbPath.setSpacing(5);
			hbPath.setPadding(new Insets(0, 5, 0, 5));
			hbPath.setAlignment(Pos.CENTER_LEFT);

			// Port Config
			Label lbPort = new Label("Port:");
			lbPort.setAlignment(Pos.CENTER_RIGHT);
			lbPort.setPrefWidth(60);

			ChoiceBox<String> cbPortName = new ChoiceBox<>();
			{
				// for first time
				String[] portNames = SerialPortList.getPortNames();
				for (String name : portNames)
					cbPortName.getItems().add(name);
			}
			cbPortName.setOnMouseClicked(e -> {
				cbPortName.getItems().setAll(); // reset
				String[] portNames = SerialPortList.getPortNames();
				for (String name : portNames)
					cbPortName.getItems().add(name);
			});

			Button btnPortOpen = new Button("Connect");
			btnPortOpen.setOnAction(e -> {
				if (btnPortOpen.getText() == "Connect") {
					String name = cbPortName.getValue();
					boolean status = openPort(name);
					if (status) {
						btnPortOpen.setText("Disconnect");
						cbPortName.setDisable(true); // lock while connected
					}
				} else if (btnPortOpen.getText() == "Disconnect") {
					boolean status = closePort();
					if (status) {
						btnPortOpen.setText("Connect");
						cbPortName.setDisable(false); // unlock
					}
				}
			});

			HBox hbPort = new HBox();
			hbPort.getChildren().addAll(lbPort, cbPortName, btnPortOpen);
			hbPort.setSpacing(5);
			hbPort.setPadding(new Insets(0, 5, 0, 5));
			hbPort.setAlignment(Pos.CENTER_LEFT);

			root.getChildren().addAll(hbImageBox, hbImageInfo, hbPath, hbPort);

			primaryStage.setScene(scene);
			primaryStage.setTitle("SerialJpgLogger");
			primaryStage.show();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	public void stop() {
		try {
			if (port != null && port.isOpened())
				port.closePort();
		} catch (SerialPortException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		launch(args);
	}
	
	// return true if error, false else
	private void updateShowPic() {
		System.out.println("List Path: "+ pathDir);
		File f = new File(pathDir);
		String list[] = f.list((dir, name) -> dir.isDirectory() && name.toLowerCase().endsWith(".jpg"));
		if (list.length > 0) {
			Collections.sort(Arrays.asList(list));
			//for (String s : list) System.out.println(s);
			String path = pathDir + "/"+list[list.length-1];
			System.out.println("Newest: "+ path);
			imageList.add(new File(path));
			System.out.println("head_imagePathList: "+head_imagePathList+", "+"imageList.size(): "+imageList.size());
			if (head_imagePathList >= imageList.size()-2) {
				head_imagePathList = imageList.size()-1;
				showPic(imageList.size()-1);
			}
			
		}
	}
	
	private void showPic(int i) {
		if (imageList.size() == 0 && i > imageList.size()) return;
		
		File file = imageList.get(i);
		Image img = new Image(file.toURI().toString());
		boolean error = img.isError();
		if (error) {
			img = new Image("assets/brokenImage.jpg");
		}
		ivLatest.setImage(img);
		tfImage.setText(file.getName());
	}

	private void savePic() {
		if (head_raw % 2 != 0)
			head_raw++; // avoid odd number of length
		byte[] tmp_hex = new byte[head_raw]; // avoid interrupt
		for (int i = 0; i < tmp_hex.length; i++)
			tmp_hex[i] = raw[i];
		raw[head_raw - 1] = '0';
		System.out.printf("head_raw: %d\n", head_raw);

		byte[] pic_hex = new byte[head_raw];
		for (int i = 0; i < pic_hex.length; i++)
			pic_hex[i] = '0';

		Date date = new Date();
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String fname = pathDir + "/log" + format.format(date);

		// convert HEX array to BIN array

		// remove non-alphabet/digit char in ascii
		// 0-9, a-f, A-F
		System.out.print("Invalid Characters: ");
		boolean isRunningZero = true;
		for (int i = 0, j = 0; i < tmp_hex.length; i++) {
			if (isRunningZero && tmp_hex[i] == '0') {
				continue; // ignore first running zero
			} else if (tmp_hex[i] >= 0x00 && tmp_hex[i] <= 0x20) {
				continue; // ignore control sequence, blank
			} else if (!((tmp_hex[i] >= '0' && tmp_hex[i] <= '9') || (tmp_hex[i] >= 'a' && tmp_hex[i] <= 'f')
					|| (tmp_hex[i] >= 'A' && tmp_hex[i] <= 'F'))) {
				isRunningZero = false;
				System.out.printf("[%d]=0x%02x, ", i, tmp_hex[i]);
				pic_hex[j] = '0';
				j++;
			} else {
				isRunningZero = false;
				pic_hex[j] = tmp_hex[i];
				j++;
			}
		}
		System.out.println();

		System.out.println("First 64 bytes of raw: ");
		for (int i = 0; i < 64; i++) {
			System.out.printf("%c", tmp_hex[i]);
			if ((i + 1) % 32 == 0)
				System.out.println();
			else if ((i + 1) % 16 == 0)
				System.out.print("  ");
			else if ((i + 1) % 2 == 0)
				System.out.print(" ");
		}

		byte[] pic_bin = null;
		try {
			Hex hex = new Hex();
			byte[] tmp_bin = hex.decode(pic_hex);
			pic_bin = new byte[tmp_bin.length];
			for (int i = 0; i < pic_bin.length; i++)
				pic_bin[i] = tmp_bin[i];
		} catch (DecoderException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();

			Alert al = new Alert(AlertType.ERROR);
			al.setContentText("ごめん私が悪い；この画面をスクショして電話してくれ" + e1.getMessage());
			al.show();

			clearBuf();
		}

		try {
			// save raw log
			PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(fname + "_raw.hex")));
			for (int i = 0; i < tmp_hex.length; i++) {
				pw.printf("%c", tmp_hex[i]);
				// 2 byte of pic_hex means 1 byte of pic_bin
				if ((i + 1) % 32 == 0)
					pw.printf("\n");
			}
			pw.close();

			if (pic_bin == null)
				return;

			// hex dump
			System.out.println("Hex Dump of first 64 bytes: ");
			for (int i = 0; i < 64; i++) {
				System.out.printf("%02x", pic_bin[i]);
				if ((i + 1) % 16 == 0)
					System.out.println();
				else if ((i + 1) % 8 == 0)
					System.out.print("  ");
				else
					System.out.print(" ");
			}

			FileOutputStream fos = new FileOutputStream(fname + ".jpg");
			fos.write(pic_bin, 0, pic_bin.length);
			fos.flush();
			fos.close();

			updateShowPic();
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();

			Alert al = new Alert(AlertType.ERROR);
			al.setContentText("ごめん私が悪い；この画面をスクショして電話してくれ" + e1.getMessage());
			al.show();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();

			Alert al = new Alert(AlertType.ERROR);
			al.setContentText("ごめん私が悪い；この画面をスクショして電話してくれ" + e1.getMessage());
			al.show();
		}
	}

	private void parsePic() {
		// detect & reset begin/end of jpg data
		// 64 char(in byte) == 128 byte
		if ((head_raw > 128) && (raw[head_raw - 1] == '0')) {

			// extract running values
			byte[] tmp = new byte[128];
			int max = 0;
			for (int i = 0; i < tmp.length; i++) {
				if (raw[head_raw - 1 - i] >= 0x00 && raw[head_raw - 1 - i] <= 0x20)
					continue; // ignore blank
				else {
					tmp[max] = raw[head_raw - 1 - i];
					max++;
				}
			}

			// if (max < 64) return; // too few to decide: end of picture or not

			int cnt = 0;
			for (int i = 0; i < tmp.length; i++) {
				if (tmp[tmp.length - 1 - i] == '0')
					cnt++;
			}
			if (cnt == max) {
				// save & reset
				// is there any non-'0'
				boolean isValid = false;
				for (int i = 0; i < head_raw; i++) {
					if (raw[i] >= 0x00 && raw[i] <= 0x20)
						continue;
					else if (raw[i] != '0') {
						System.out.println("Valid Picture");
						isValid = true;
						break;
					}
				}

				if (isValid)
					savePic();

				clearBuf();
			}
		}
	}

	private void clearBuf() {
		for (int i = 0; i < head_raw; i++)
			raw[i] = '0';
		head_raw = 0;
	}

	// return true if success, false if fail
	private boolean openPort(String name) {
		if (name == null) {
			Alert al = new Alert(AlertType.ERROR);
			al.setContentText("Can't open port: Select Port first!");
			al.show();
			return false;
		}

		if (port != null && port.isOpened()) {
			Alert al = new Alert(AlertType.ERROR);
			al.setContentText("Can't open port: Port is already open.");
			al.show();
			return false;
		}

		port = new SerialPort(name);
		try {
			port.openPort();
			port.setParams(SerialPort.BAUDRATE_115200, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
					SerialPort.PARITY_NONE);
			System.out.println("Port " + name + " Opened");
			int mask = SerialPort.MASK_RXCHAR;
			port.setEventsMask(mask);
			port.addEventListener(el -> {
				if (el.isRXCHAR()) { // data is available
					int n = el.getEventValue();
					// System.out.println("getEventValue: "+ n);
					if (n > 0) {
						try {
							byte[] buf = port.readBytes(n);
							for (int i = 0, j = 0; i < n; i++) {
								raw[head_raw + j] = buf[i];
								j++;
							}
							head_raw += n;
							parsePic();
						} catch (SerialPortException ex) {
							System.out.println(ex);
						}

					}
				}
			});

		} catch (SerialPortException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			Alert al = new Alert(AlertType.ERROR);
			al.setContentText("Can't Open port: " + e1.getMessage());
			al.show();
			return false;
		}

		clearBuf();
		return true;
	}

	private boolean closePort() {
		try {
			if (port != null && !port.isOpened())
				return false;
			boolean status = port.closePort();
			if (status)
				System.out.println("Port Closed");
			else
				return false;
		} catch (SerialPortException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			Alert al = new Alert(AlertType.ERROR, 
					"Can't Close port: " + e1.getMessage() + ", Force Close?",
					ButtonType.YES, ButtonType.NO);
			al.setTitle("Force Close?");
			Optional<ButtonType> res = al.showAndWait();
			if (res.get() == ButtonType.YES) return true;
			else return false;
		}
		return true;
	}

}
