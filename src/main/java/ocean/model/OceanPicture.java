package ocean.model;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HexFormat;

import javax.imageio.ImageIO;

/**
 * <pre>
 * Hilfsmethoden zur Konvertierung von BufferedImage zu Hex-String und umgekehrt.
 * Ermöglicht das Speichern und Laden von PNG-Bildern.
 * </pre>
 */
public class OceanPicture {

	public static BufferedImage convertHexString2Image(String hexValues) {
		HexFormat hexFormat = HexFormat.of();
		byte[] bytes = hexFormat.parseHex(hexValues);
    	ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
    	try {
			return ImageIO.read(bin);
		} catch (IOException e) {
		}
		return null;
	}

	public static String convertImage2HexString(BufferedImage bimg) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		String pngStr = null;
    	try {
			if(ImageIO.write(bimg, "png", bos)) {
				byte[] bytes = bos.toByteArray();
				HexFormat hexFormat = HexFormat.of();
				pngStr =  hexFormat.formatHex(bytes);
			}
		} catch (IOException e) {
		}
		return pngStr;
	}
	
	public static boolean saveAsPNG(BufferedImage bimg, String filename) {
		if (bimg != null && filename !=null) {
			if (!filename.endsWith(".png")) {
				filename += ".png";
			}
			File imageFile = new File(filename);
			try {
				boolean ok = ImageIO.write(bimg, "png", imageFile);
				return ok;
			} catch (IOException e) {
			}
		}
		return false;
	}
	
	public static BufferedImage loadPNG(String filename) {
		if (filename !=null) {
			if (!filename.endsWith(".png")) {
				filename += ".png";
			}
			File imageFile = new File(filename);
			try {
				BufferedImage bimg = ImageIO.read(imageFile);
				return bimg;
			} catch (IOException e) {
			}
		}
		return null;
	}
	
}
