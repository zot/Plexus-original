package p2pmud;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.net.URL;
import org.jdesktop.swingx.JXImagePanel;

public class PlexusImagePanel extends JXImagePanel {
	public Raster raster;
	public int sample[];
	public Rectangle imageBounds;

	public PlexusImagePanel(URL url) {
		super(url);
	}
	public boolean contains(int x, int y) {
		if (!super.contains(x, y)) {
			return false;
		}
		if (imageBounds == null) {
			imageBounds = getRaster().getBounds();
		}
		if (imageBounds.contains(x, y)) {
			sample = getRaster().getPixel(x, y, sample);
			return sample[3] != 0;
		}
		return false;
	}
	public Raster getRaster() {
		if (raster == null && getImage() instanceof BufferedImage) {
			raster = ((BufferedImage)getImage()).getRaster();
		}
		return raster;
	}
}
