package p2pmud

import net.miginfocom.swing.MigLayout
import groovy.swing.SwingBuilder
import java.awt.Dimension


public class MessageBox
{
	static Show(title, msg) {
		def f
		new SwingBuilder().build {
				f = frame(title: title, windowClosing: {System.exit(0)}, layout: new MigLayout('fillx'), pack: true, show: true) {
				
				label(text:msg, constraints:('span 2, wrap'))
				button(text: "OK", actionPerformed: {f.dispose(); })
			}
			f.setLocation(500, 500)
			f.size = [500, (int)f.size.height] as Dimension
		}
		
	}
}
