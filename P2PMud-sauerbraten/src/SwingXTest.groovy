import groovy.swing.SwingBuilder
import java.awt.Font
import javax.swing.UIManager
import javax.swing.plaf.FontUIResource
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.border.DropShadowBorder
import java.awt.Color
import org.jdesktop.swingx.painter.GlossPainter
import groovy.swing.SwingXBuilder

public class SwingXTest {
	def static swing
	public static void main(String[] args) {
		System.setProperty("sun.java2d.d3d", "false");
		System.getProperties().each {
			println "$it.key: $it.value"
		}
		useSwingX()
	}
	def static useSwingX() {
		try {
		   UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
		} catch (Exception e) {e.printStackTrace()}
//		UIManager.put("Label.font", new FontUIResource("SansSerif", Font.PLAIN, 12))
		swing = new SwingXBuilder()
		swing.frame(title: "hello", size: [300,300], show: true) {
			def makeTitlePainter = {label ->
				compoundPainter() {
					mattePainter(fillPaint:Color.BLACK)
		            textPainter(text: label, font: new FontUIResource("SansSerif", Font.BOLD, 12), fillPaint: new Color(1.0f,1.0f,1.0f,1.0f))
					glossPainter(paint:new Color(1.0f,1.0f,1.0f,0.2f), position:GlossPainter.GlossPosition.TOP)
				}
			}
			titledPanel(title: ' ', titlePainter: makeTitlePainter('Plexus GUI Test'), border: new DropShadowBorder(Color.BLACK, 15)) {
				panel(layout: new MigLayout('fillx')) {
					checkBox(text: 'check', constraints: 'wrap')
					comboBox(items: ['one', 'two', 'three'], constraints: 'wrap')
					button(text: 'button', constraints: 'wrap')
				}
			}
		}
	}
	def static useSwing() {
		try {
		   UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
		} catch (Exception e) {e.printStackTrace()}
//		UIManager.put("Label.font", new FontUIResource("SansSerif", Font.PLAIN, 12))
		swing = new SwingBuilder()
		swing.frame(title: "hello", size: [300,300], show: true) {
			panel(layout: new MigLayout('fillx')) {
				checkBox(text: 'check', constraints: 'wrap')
				comboBox(items: ['one', 'two', 'three'], constraints: 'wrap')
				button(text: 'button', constraints: 'wrap')
			}
		}
	}
}
