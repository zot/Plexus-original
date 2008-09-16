package p2pmud

import net.miginfocom.swing.MigLayout
import groovy.swing.SwingBuilder
import java.awt.Dimension


public class MessageBox
{
	static Show(title, msg) {
		def f
		new SwingBuilder().build {
				f = dialog(title: title, windowClosing: {System.exit(0)}, layout: new MigLayout('fillx'), pack: true, modal: true) {
				
				label(text:msg, constraints:('span 2, wrap'))
				button(text: "OK", actionPerformed: {f.show(false); })
			}
			f.setLocation(550, 550)
			f.size = [500, (int)f.size.height] as Dimension
			f.pack()
			f.visible = true
		}
		
	}
	
	static AreYouSure(title, msg) {
		def f, result
		new SwingBuilder().build {
				f = dialog(title: title, windowClosing: {System.exit(0)}, layout: new MigLayout('fillx'), pack: true, modal: true) {
				
				label(text:msg, constraints:('span 2, wrap'))
				button(text: "OK", actionPerformed: {f.show(false); result = true})
				button(text: "Cancel", actionPerformed: {f.show(false); result = false})
			}
			f.setLocation(550, 550)
			f.size = [500, (int)f.size.height] as Dimension
			f.pack()
			f.visible = true
		}
		return result;
	}
}
